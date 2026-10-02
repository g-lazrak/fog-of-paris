#!/usr/bin/perl
# Turns Paris's walkable public ways (OpenStreetMap, via Overpass) into the list
# of 50 m grid cells that a walkable way crosses. Used by the "surrounded block"
# rule (owner, 2026-10-03): a fog patch surrounded by walked cells clears only
# if no walkable way runs inside it.
# Usage (Git Bash, from tools/walkable):
#   curl -s --data-urlencode "data@walkable.overpassql" https://overpass.kumi.systems/api/interpreter > walkable.json
#   perl make_walkable_cells.pl walkable.json ../../app/src/main/assets/walkable_cells.txt
# Output: one line per grid row, "y:x1..x2,x3" (runs of walkable cells; x can
# be negative west of the grid origin, in the Bois de Boulogne).
use strict;
use warnings;
use POSIX qw(floor);

# Same grid as Grid.kt.
my $LAT_MIN = 48.815;
my $LON_MIN = 2.250;
my $M_PER_DEG_LAT = 111320.0;
my $M_PER_DEG_LON = $M_PER_DEG_LAT * cos(48.85 * 3.141592653589793 / 180);
my $CELL = 50.0;
# A way marks a cell only where it passes through the cell's middle: a street
# running along the edge of a block must not mark the block's own cells.
my $EDGE_MARGIN = 10.0;
my $STEP = 4.0;

my ($input, $output) = @ARGV;
die "usage: make_walkable_cells.pl walkable.json walkable_cells.txt\n" unless $input && $output;
open my $in, '<', $input or die "$input: $!";
my $json = do { local $/; <$in> };
close $in;

my %cells;
my $ways = 0;
while ($json =~ /"geometry"\s*:\s*\[(.*?)\]/gs) {
    my $geometry = $1;
    my @points;
    while ($geometry =~ /"lat"\s*:\s*(-?[\d.]+)\s*,\s*"lon"\s*:\s*(-?[\d.]+)/g) {
        # Metres east / north of the grid origin.
        push @points, [($2 - $LON_MIN) * $M_PER_DEG_LON, ($1 - $LAT_MIN) * $M_PER_DEG_LAT];
    }
    $ways++;
    for my $i (1 .. $#points) {
        my ($x0, $y0) = @{$points[$i - 1]};
        my ($x1, $y1) = @{$points[$i]};
        my $steps = int(sqrt(($x1 - $x0) ** 2 + ($y1 - $y0) ** 2) / $STEP) + 1;
        for my $s (0 .. $steps) {
            my $x = $x0 + ($x1 - $x0) * $s / $steps;
            my $y = $y0 + ($y1 - $y0) * $s / $steps;
            my $cx = floor($x / $CELL);
            my $cy = floor($y / $CELL);
            my $inside_x = $x - $cx * $CELL;
            my $inside_y = $y - $cy * $CELL;
            next if $inside_x < $EDGE_MARGIN || $inside_x > $CELL - $EDGE_MARGIN;
            next if $inside_y < $EDGE_MARGIN || $inside_y > $CELL - $EDGE_MARGIN;
            $cells{$cy}{$cx} = 1;
        }
    }
}

open my $out, '>', $output or die "$output: $!";
my $count = 0;
for my $y (sort { $a <=> $b } keys %cells) {
    my @xs = sort { $a <=> $b } keys %{$cells{$y}};
    $count += @xs;
    my @runs;
    my ($start, $prev) = ($xs[0], $xs[0]);
    for my $x (@xs[1 .. $#xs], undef) {
        if (defined $x && $x == $prev + 1) { $prev = $x; next }
        push @runs, $start == $prev ? $start : "$start..$prev";
        ($start, $prev) = ($x, $x);
    }
    print $out "$y:" . join(',', @runs) . "\n";
}
close $out;
print "$ways ways, $count walkable cells, " . scalar(keys %cells) . " rows\n";
