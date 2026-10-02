# Bedrock particle direction mode

`bedrock_particle_direction_mode.json` is Mojang's schema snapshot from
[bedrock-samples commit 46ba6ea985fb5a92d79a9419198f10dda14c199d](https://github.com/Mojang/bedrock-samples/blob/46ba6ea985fb5a92d79a9419198f10dda14c199d/metadata/json_schemas/client/particles/1.21.10/particle_appearance_billboard%20direction_settings_mode.json).
The repository's terms are included in `LICENSE-Mojang.txt`.

The engine enum is `custom`, while `custom_direction` is the vector property.
The same spelling is used in Mojang's format 1.10.0
[shriek particle](https://github.com/Mojang/bedrock-samples/blob/46ba6ea985fb5a92d79a9419198f10dda14c199d/resource_pack/particles/shriek.json).
The prose billboard reference incorrectly lists `custom_direction` as a mode.
Both release pack validators check the actual engine enum, including all
upright planes and attack rays, to avoid repeating that mistake.
