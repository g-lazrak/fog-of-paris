#!/usr/bin/perl
# Builds the app's two map styles from OpenFreeMap's "liberty" style.
# Usage (Git Bash, from the project root):
#   curl -s https://tiles.openfreemap.org/styles/liberty > tools/mapstyles/liberty.json
#   perl tools/mapstyles/make_styles.pl tools/mapstyles/liberty.json app/src/main/assets
# Owner's choices (2026-10-03): no bus/metro/RER stops and no place icons at all,
# green only on real parks/gardens/trees, colours of an old explorer's map;
# aerial photos from IGN with street names on top.
use strict;
use warnings;
use JSON::PP;

my ($input, $out_dir) = @ARGV;
die "usage: make_styles.pl liberty.json output_dir\n" unless $input && $out_dir;

open my $in, '<', $input or die "$input: $!";
my $liberty = do { local $/; decode_json(<$in>) };
close $in;

my $json = JSON::PP->new->canonical->utf8;
my $french_name = ['coalesce', ['get', 'name:fr'], ['get', 'name']];

sub clone { decode_json(encode_json($_[0])) }

# French place names everywhere, as on the rest of the app.
sub french_labels {
    my ($layer) = @_;
    my $field = $layer->{layout} && $layer->{layout}{'text-field'};
    $layer->{layout}{'text-field'} = $french_name if $field && encode_json([$field]) =~ /"name/;
}

# --- Explorateur ---------------------------------------------------------
# "park" in OpenMapTiles holds protected zones (UNESCO "Paris, rives de la
# Seine" covers the whole centre), not greenery: real parks are in landcover.
my $removed = qr/^(park|park_outline|natural_earth)$|transit|major_rail|one_way|shield|airport|aeroway|building-3d|^poi/;

my $explorer = clone($liberty);
delete $explorer->{sources}{ne2_shaded};
$explorer->{name} = 'Fog of Paris - Explorateur';
$explorer->{layers} = [grep { $_->{id} !~ $removed } @{$explorer->{layers}}];

my %c = (
    paper => '#EFE6D2', water => '#6E9BBB', building => '#DCCDB0', building_edge => '#C6B48F',
    green => '#B4C98A', sport => '#D3DBB4', cemetery => '#D2D6B2', residential => '#EAE0CA',
    institution => '#E7D8C4', casing => '#CDBB98', path => '#B0966B', motorway => '#EDCB84',
    primary => '#F5DFA9', secondary => '#FBF0D4', street => '#FFFBF0', boundary => '#B8A98A',
    ink => '#3B3226', water_ink => '#2F5873', halo => 'rgba(247,241,227,0.92)',
);

for my $l (@{$explorer->{layers}}) {
    my $id = $l->{id};
    my $p = $l->{paint} ||= {};
    french_labels($l);
    if ($l->{type} eq 'background') {
        $p->{'background-color'} = $c{paper};
    } elsif ($l->{type} eq 'fill') {
        if ($id eq 'water') { $p->{'fill-color'} = $c{water} }
        elsif ($id eq 'building') { $p->{'fill-color'} = $c{building}; $p->{'fill-outline-color'} = $c{building_edge} }
        elsif ($id =~ /wood|grass/) { $p->{'fill-color'} = $c{green}; $p->{'fill-opacity'} = 0.8 }
        elsif ($id =~ /pitch|track/) { $p->{'fill-color'} = $c{sport} }
        elsif ($id =~ /cemetery/) { $p->{'fill-color'} = $c{cemetery} }
        elsif ($id =~ /residential/) { $p->{'fill-color'} = $c{residential} }
        elsif ($id =~ /hospital|school/) { $p->{'fill-color'} = $c{institution} }
    } elsif ($l->{type} eq 'line') {
        if ($id =~ /^waterway/) { $p->{'line-color'} = $c{water} }
        elsif ($id =~ /casing/) { $p->{'line-color'} = $c{casing} }
        elsif ($id =~ /path_pedestrian/) { $p->{'line-color'} = $c{path} }
        elsif ($id =~ /motorway/) { $p->{'line-color'} = $c{motorway} }
        elsif ($id =~ /trunk_primary/) { $p->{'line-color'} = $c{primary} }
        elsif ($id =~ /secondary_tertiary/) { $p->{'line-color'} = $c{secondary} }
        elsif ($id =~ /^(road|bridge|tunnel)_/) { $p->{'line-color'} = $c{street} }
        elsif ($id =~ /boundary/) { $p->{'line-color'} = $c{boundary} }
    } elsif ($l->{type} eq 'symbol') {
        $p->{'text-color'} = $id =~ /water/ ? $c{water_ink} : $c{ink};
        $p->{'text-halo-color'} = $c{halo};
    }
}

# --- Photos aériennes ----------------------------------------------------
# IGN orthophotos (Géoplateforme, Licence Ouverte, no key). Tiles drawn at
# 128 px so they stay sharp on a high-density screen; IGN stops at zoom 19.
my $label_ids = qr/^(water_name_point_label|water_name_line_label|highway-name-path|highway-name-minor|highway-name-major|label_other|label_village|label_town|label_city)$/;
my @labels;
for my $l (@{clone($liberty)->{layers}}) {
    next unless $l->{id} =~ $label_ids;
    french_labels($l);
    $l->{paint} = {
        %{$l->{paint} || {}},
        'text-color' => ($l->{id} =~ /water/ ? '#CFE3F2' : '#FFFFFF'),
        'text-halo-color' => 'rgba(11,16,32,0.85)',
        'text-halo-width' => 1.6,
        'text-halo-blur' => 0.4,
    };
    push @labels, $l;
}

my $aerial = {
    version => 8,
    name => 'Fog of Paris - Photos aériennes',
    glyphs => $liberty->{glyphs},
    sources => {
        photos => {
            type => 'raster',
            tiles => ['https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=ORTHOIMAGERY.ORTHOPHOTOS&STYLE=normal&TILEMATRIXSET=PM&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&FORMAT=image/jpeg'],
            tileSize => 128,
            maxzoom => 19,
            attribution => '© IGN',
        },
        openmaptiles => $liberty->{sources}{openmaptiles},
    },
    layers => [
        { id => 'background', type => 'background', paint => { 'background-color' => '#1B2340' } },
        { id => 'photos', type => 'raster', source => 'photos' },
        @labels,
    ],
};

for ([$explorer, 'map_style_explorer.json'], [$aerial, 'map_style_aerial.json']) {
    my ($style, $name) = @$_;
    open my $out, '>', "$out_dir/$name" or die "$out_dir/$name: $!";
    print $out $json->encode($style);
    close $out;
    printf "%s: %d layers\n", $name, scalar @{$style->{layers}};
}
