# No blanket keep rules: Widget APIs and host extensions are referenced directly.
# Android resource processing keeps View classes/constructors referenced by host XML.
# The library does not instantiate host sources, templates or effects by reflection.
# Hosts that add their own reflective lookup must supply rules for that lookup.
