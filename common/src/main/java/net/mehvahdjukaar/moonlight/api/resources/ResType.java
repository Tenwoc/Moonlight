package net.mehvahdjukaar.moonlight.api.resources;


import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

public enum ResType {
    GENERIC("%s"),
    TAGS("tags/%s.json"),
    LOOT_TABLES("loot_table/%s.json"),
    BLOCK_LOOT_TABLES("loot_table/blocks/%s.json"),
    RECIPES("recipe/%s.json"),
    ENCHANTMENTS("enchantment/%s.json"),
    ADVANCEMENTS("advancement/%s.json"),
    CONFIGURED_FEATURES("worldgen/configured_feature/%s.json"),
    STRUCTURE("worldgen/structure/%s.json"),
    STRUCTURE_SET("worldgen/structure_set/%s.json"),
    TEMPLATE_POOL("worldgen/template_pool/%s.json"),

    LANG("lang/%s.json"),
    TEXTURES("textures/%s.png"),
    BLOCK_TEXTURES("textures/block/%s.png"),
    ITEM_TEXTURES("textures/item/%s.png"),
    ENTITY_TEXTURES("textures/entity/%s.png"),
    PARTICLE_TEXTURES("textures/particle/%s.png"),
    MCMETA("textures/%s.png.mcmeta"),
    BLOCK_MCMETA("textures/block/%s.png.mcmeta"),
    ITEM_MCMETA("textures/item/%s.png.mcmeta"),
    MODELS("models/%s.json"),
    BLOCK_MODELS("models/block/%s.json"),
    ITEM_MODELS("models/item/%s.json"),
    //item model definitions, the thing that picks which model an item stack shows. models/item only holds geometry
    ITEMS("items/%s.json"),
    BLOCKSTATES("blockstates/%s.json"),
    PARTICLES("particles/%s.json"),
    MOB_EFFECT_TEXTURES("mob_effect/%s.json"),
    JSON("%s.json"),
    PNG("%s.png");

    private final String loc;

    ResType(String loc) {
        this.loc = loc;
    }


    public Identifier getPath(Identifier relativeLocation) {
        return relativeLocation.withPath(String.format(this.loc, relativeLocation.getPath()));
    }

    public Identifier getPath(String relativeLocation) {
        return this.getPath(Identifier.parse(relativeLocation));
    }

    public static Identifier getTagPath(TagKey<?> tag) {
        return TAGS.getPath(tag.location().withPrefix(tag.registry().identifier().getPath() + "/"));
    }

}