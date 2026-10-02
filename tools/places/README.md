# Lieux des collections

`places.tsv` est la liste de référence des 145 lieux (7 séries). Elle est
convertie en `app/src/main/assets/places.geojson`, le fichier lu par l'app.

- **Noms** : choisis à la main (liste validée par le propriétaire le 2026-09-27).
- **Coordonnées** : OpenStreetMap (© contributeurs OSM, licence ODbL), extraites
  une fois via l'API Overpass, en prenant le centre de l'objet le plus
  représentatif (le pont, le bâtiment, la place — pas un arrêt de bus).
- **radius** (m) : distance à laquelle il faut passer pour que le lieu compte.
  60 pour un pont (il faut le traverser), jusqu'à 150 pour les grandes places
  à rond-point (Étoile, Concorde) dont les trottoirs sont loin du centre.
- Ponts exclus car non traversables à pied : Pont Amont, Pont Aval
  (périphérique) et Viaduc d'Austerlitz (métro).

Pour régénérer le GeoJSON après une modification du TSV (Git Bash) :

```sh
awk -F'\t' 'BEGIN{print "{\"type\":\"FeatureCollection\",\"features\":["} NR>1 {gsub(/"/,"\\\"",$3); printf "%s{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[%s,%s]},\"properties\":{\"id\":\"%s\",\"name\":\"%s\",\"set\":\"%s\",\"radius\":%s}}", (NR>2?",\n":""), $5, $4, $2, $3, $1, $6} END{print "\n]}"}' tools/places/places.tsv > app/src/main/assets/places.geojson
```

Le test `ParisBoundaryTest` vérifie que chaque lieu est bien dans Paris.

## Trésors cachés

`treasures.tsv` : un trésor par quartier (80), avec `hint` (indice affiché
tant qu'il n'est pas trouvé), `story` (2-3 phrases d'histoire, montrées une
fois trouvé) et `wiki` (titre exact de l'article Wikipédia en français, vide
s'il n'y en a pas). Les titres ont été vérifiés avec l'API de Wikipédia.

Pour régénérer `app/src/main/assets/treasures.geojson` (Git Bash) :

```sh
awk -F'\t' 'function q(s) { gsub(/"/, "\\\"", s); return "\"" s "\"" } BEGIN{print "{\"type\":\"FeatureCollection\",\"features\":["} NR>1 { wiki = ($8 != "" ? ",\"wiki\":" q($8) : ""); printf "%s{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[%s,%s]},\"properties\":{\"id\":%s,\"name\":%s,\"set\":\"TREASURES\",\"radius\":%s,\"hint\":%s,\"story\":%s%s}}", (NR>2?",\n":""), $4, $3, q($1), q($2), $5, q($6), q($7), wiki } END{print "\n]}"}' tools/places/treasures.tsv > app/src/main/assets/treasures.geojson
```
