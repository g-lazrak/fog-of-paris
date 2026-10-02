[out:json][timeout:600];
area(id:3600007444)->.paris;
way(area.paris)
  ["highway"~"^(trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|living_street|pedestrian|footway|path|steps|track|cycleway|bridleway)$"]
  ["access"!~"^(private|no)$"]
  ["foot"!~"^(no|private)$"]
  ["tunnel"!="yes"]
  ["indoor"!="yes"]
  ["level"!~"^-"];
out skel geom;
