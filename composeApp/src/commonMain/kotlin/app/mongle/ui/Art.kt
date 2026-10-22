package app.mongle.ui

import app.mongle.generated.resources.Res
import app.mongle.generated.resources.*
import app.mongle.model.Pose
import app.mongle.model.Stage
import app.mongle.model.roomTheme
import org.jetbrains.compose.resources.DrawableResource

enum class PetAct { EGG, IDLE, BLINK, HAPPY, EAT, SLEEP, WALK }

fun petAct(stage: Stage, pose: Pose, energy: Int, status: String): PetAct {
    if (stage == Stage.EGG || pose == Pose.EGG) return PetAct.EGG
    if ("먹였" in status || "Fed" in status) return PetAct.EAT
    if (pose == Pose.SLEEP || energy <= 15) return PetAct.SLEEP
    if (pose == Pose.WALK || pose == Pose.OUT) return PetAct.WALK
    if (pose == Pose.HAPPY || "완료" in status || "자랐" in status || "Done" in status || "Grew" in status) return PetAct.HAPPY
    return PetAct.IDLE
}

fun petAssetId(speciesId: String, stage: Stage, act: PetAct): String {
    if (stage == Stage.EGG || act == PetAct.EGG) return "egg"
    val species = speciesId.takeUnless { it.isBlank() || it == "base" || it == "sp_peach" }
    if (species != null) {
        val pose = when (act) {
            PetAct.SLEEP -> "sleep"
            PetAct.HAPPY -> "happy"
            PetAct.EAT -> "eat"
            PetAct.WALK -> "walk_left"
            PetAct.BLINK -> "blink"
            PetAct.EGG, PetAct.IDLE -> null
        }
        if (pose != null) {
            val framed = "${species}_$pose"
            if (SpeciesPoses.has(framed)) return framed
        }
        return species
    }
    return when (act) {
        PetAct.SLEEP -> "base_sleep"
        PetAct.HAPPY -> "base_happy"
        PetAct.EAT -> "base_eat"
        PetAct.WALK -> "base_walk_left"
        PetAct.BLINK -> "base_blink"
        PetAct.EGG -> "egg"
        PetAct.IDLE -> "base_idle"
    }
}

fun roomAssetId(theme: String, night: Boolean, square: Boolean): String {
    val tone = when (theme) {
        "mint" -> "mint"
        "starry" -> "starry"
        else -> "wood"
    }
    val day = if (night) "night" else "day"
    val shape = if (square) "1x1" else "3x4"
    return "room_${tone}_${day}_${shape}"
}

fun roomAssetFor(wallpaperId: String?, night: Boolean, square: Boolean): String =
    roomAssetId(roomTheme(wallpaperId), night, square)

object Art {
    fun known(id: String): Boolean = id in ids
    fun drawable(id: String): DrawableResource = SpeciesPoses.drawable(id) ?: when (id) {
        "acorn" -> Res.drawable.acorn
        "app_icon" -> Res.drawable.app_icon
        "babydragon" -> Res.drawable.babydragon
        "barley" -> Res.drawable.barley
        "base_blink" -> Res.drawable.base_blink
        "base_eat" -> Res.drawable.base_eat
        "base_happy" -> Res.drawable.base_happy
        "base_idle" -> Res.drawable.base_idle
        "base_sleep" -> Res.drawable.base_sleep
        "base_walk_left" -> Res.drawable.base_walk_left
        "bathtub" -> Res.drawable.bathtub
        "bear" -> Res.drawable.bear
        "beret" -> Res.drawable.beret
        "blanket" -> Res.drawable.blanket
        "bookshelf" -> Res.drawable.bookshelf
        "bow_tie" -> Res.drawable.bow_tie
        "bunny_ears" -> Res.drawable.bunny_ears
        "butter" -> Res.drawable.butter
        "cat" -> Res.drawable.cat
        "cat_tower" -> Res.drawable.cat_tower
        "chestnut" -> Res.drawable.chestnut
        "chick" -> Res.drawable.chick
        "cloud" -> Res.drawable.cloud
        "cloud_lamp" -> Res.drawable.cloud_lamp
        "clover" -> Res.drawable.clover
        "cookie" -> Res.drawable.cookie
        "crown" -> Res.drawable.crown
        "cushion_bed" -> Res.drawable.cushion_bed
        "desk" -> Res.drawable.desk
        "donut" -> Res.drawable.donut
        "duck" -> Res.drawable.duck
        "egg" -> Res.drawable.egg
        "empty_gifts" -> Res.drawable.empty_gifts
        "empty_schedule" -> Res.drawable.empty_schedule
        "feather" -> Res.drawable.feather
        "fish_tank" -> Res.drawable.fish_tank
        "floor_lamp" -> Res.drawable.floor_lamp
        "flower_crown" -> Res.drawable.flower_crown
        "flower_pot" -> Res.drawable.flower_pot
        "food_bowl" -> Res.drawable.food_bowl
        "fox" -> Res.drawable.fox
        "frog" -> Res.drawable.frog
        "ghost" -> Res.drawable.ghost
        "greentea" -> Res.drawable.greentea
        "hamster" -> Res.drawable.hamster
        "hen" -> Res.drawable.hen
        "hoodie" -> Res.drawable.hoodie
        "injeolmi" -> Res.drawable.injeolmi
        "knit_scarf" -> Res.drawable.knit_scarf
        "koala" -> Res.drawable.koala
        "lemon" -> Res.drawable.lemon
        "letter" -> Res.drawable.letter
        "lion" -> Res.drawable.lion
        "macaron" -> Res.drawable.macaron
        "mandu" -> Res.drawable.mandu
        "maple_leaf" -> Res.drawable.maple_leaf
        "milk" -> Res.drawable.milk
        "milk_bottle" -> Res.drawable.milk_bottle
        "mint_rug" -> Res.drawable.mint_rug
        "moon" -> Res.drawable.moon
        "mushroom" -> Res.drawable.mushroom
        "mushroom_item" -> Res.drawable.mushroom_item
        "octopus" -> Res.drawable.octopus
        "onboarding" -> Res.drawable.onboarding
        "otter" -> Res.drawable.otter
        "owl" -> Res.drawable.owl
        "palette" -> Res.drawable.palette
        "panda" -> Res.drawable.panda
        "peach" -> Res.drawable.peach
        "pebble" -> Res.drawable.pebble
        "penguin" -> Res.drawable.penguin
        "piano" -> Res.drawable.piano
        "picture_frame" -> Res.drawable.picture_frame
        "pig" -> Res.drawable.pig
        "pudding" -> Res.drawable.pudding
        "puppy" -> Res.drawable.puppy
        "rabbit" -> Res.drawable.rabbit
        "rain_boots" -> Res.drawable.rain_boots
        "rainbow" -> Res.drawable.rainbow
        "raincoat" -> Res.drawable.raincoat
        "ribbon" -> Res.drawable.ribbon
        "rice_ball" -> Res.drawable.rice_ball
        "room_mint_day_1x1" -> Res.drawable.room_mint_day_1x1
        "room_mint_day_3x4" -> Res.drawable.room_mint_day_3x4
        "room_mint_night_1x1" -> Res.drawable.room_mint_night_1x1
        "room_mint_night_3x4" -> Res.drawable.room_mint_night_3x4
        "room_starry_day_1x1" -> Res.drawable.room_starry_day_1x1
        "room_starry_day_3x4" -> Res.drawable.room_starry_day_3x4
        "room_starry_night_1x1" -> Res.drawable.room_starry_night_1x1
        "room_starry_night_3x4" -> Res.drawable.room_starry_night_3x4
        "room_wood_day_1x1" -> Res.drawable.room_wood_day_1x1
        "room_wood_day_3x4" -> Res.drawable.room_wood_day_3x4
        "room_wood_night_1x1" -> Res.drawable.room_wood_night_1x1
        "room_wood_night_3x4" -> Res.drawable.room_wood_night_3x4
        "round_chair" -> Res.drawable.round_chair
        "round_table" -> Res.drawable.round_table
        "seal" -> Res.drawable.seal
        "seashell" -> Res.drawable.seashell
        "shark" -> Res.drawable.shark
        "sheep" -> Res.drawable.sheep
        "snowman" -> Res.drawable.snowman
        "sofa" -> Res.drawable.sofa
        "sprout" -> Res.drawable.sprout
        "sprout_hat" -> Res.drawable.sprout_hat
        "squirrel" -> Res.drawable.squirrel
        "star" -> Res.drawable.star
        "star_piece" -> Res.drawable.star_piece
        "straw_hat" -> Res.drawable.straw_hat
        "strawberry" -> Res.drawable.strawberry
        "strawberry_cake" -> Res.drawable.strawberry_cake
        "string_lights" -> Res.drawable.string_lights
        "sunflower" -> Res.drawable.sunflower
        "sunglasses" -> Res.drawable.sunglasses
        "sweetpotato" -> Res.drawable.sweetpotato
        "tiger" -> Res.drawable.tiger
        "tv" -> Res.drawable.tv
        "wall_clock" -> Res.drawable.wall_clock
        "wardrobe" -> Res.drawable.wardrobe
        "whale" -> Res.drawable.whale
        "wildflower" -> Res.drawable.wildflower
        "window" -> Res.drawable.window
        "wood_bed" -> Res.drawable.wood_bed
        else -> Res.drawable.base_idle
    }
    fun pet(speciesId: String, stage: Stage, act: PetAct): DrawableResource =
        drawable(petAssetId(speciesId, stage, act))
    fun room(theme: String, night: Boolean, square: Boolean): DrawableResource =
        drawable(roomAssetId(theme, night, square))
    private val ids = setOf(
        "acorn",
        "app_icon",
        "babydragon",
        "barley",
        "base_blink",
        "base_eat",
        "base_happy",
        "base_idle",
        "base_sleep",
        "base_walk_left",
        "bathtub",
        "bear",
        "beret",
        "blanket",
        "bookshelf",
        "bow_tie",
        "bunny_ears",
        "butter",
        "cat",
        "cat_tower",
        "chestnut",
        "chick",
        "cloud",
        "cloud_lamp",
        "clover",
        "cookie",
        "crown",
        "cushion_bed",
        "desk",
        "donut",
        "duck",
        "egg",
        "empty_gifts",
        "empty_schedule",
        "feather",
        "fish_tank",
        "floor_lamp",
        "flower_crown",
        "flower_pot",
        "food_bowl",
        "fox",
        "frog",
        "ghost",
        "greentea",
        "hamster",
        "hen",
        "hoodie",
        "injeolmi",
        "knit_scarf",
        "koala",
        "lemon",
        "letter",
        "lion",
        "macaron",
        "mandu",
        "maple_leaf",
        "milk",
        "milk_bottle",
        "mint_rug",
        "moon",
        "mushroom",
        "mushroom_item",
        "octopus",
        "onboarding",
        "otter",
        "owl",
        "palette",
        "panda",
        "peach",
        "pebble",
        "penguin",
        "piano",
        "picture_frame",
        "pig",
        "pudding",
        "puppy",
        "rabbit",
        "rain_boots",
        "rainbow",
        "raincoat",
        "ribbon",
        "rice_ball",
        "room_mint_day_1x1",
        "room_mint_day_3x4",
        "room_mint_night_1x1",
        "room_mint_night_3x4",
        "room_starry_day_1x1",
        "room_starry_day_3x4",
        "room_starry_night_1x1",
        "room_starry_night_3x4",
        "room_wood_day_1x1",
        "room_wood_day_3x4",
        "room_wood_night_1x1",
        "room_wood_night_3x4",
        "round_chair",
        "round_table",
        "seal",
        "seashell",
        "shark",
        "sheep",
        "snowman",
        "sofa",
        "sprout",
        "sprout_hat",
        "squirrel",
        "star",
        "star_piece",
        "straw_hat",
        "strawberry",
        "strawberry_cake",
        "string_lights",
        "sunflower",
        "sunglasses",
        "sweetpotato",
        "tiger",
        "tv",
        "wall_clock",
        "wardrobe",
        "whale",
        "wildflower",
        "window",
        "wood_bed",
    )
}

