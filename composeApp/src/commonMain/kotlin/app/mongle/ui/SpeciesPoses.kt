package app.mongle.ui

import org.jetbrains.compose.resources.DrawableResource
import app.mongle.generated.resources.Res
import app.mongle.generated.resources.*

/**
 * Interim per-species key poses (blink, happy, sleep, eat, walk_left).
 * The live pet crossfades between these until a rigged Rive file exists for the species.
 * tiger, whale, and sunflower walks are three-quarter views and can be mirrored.
 * sweetpotato's walk is missing limbs, so the stride stays small and unmirrored.
 */
object SpeciesPoses {
    const val count: Int = 250
    fun has(id: String): Boolean = id in ids
    fun mirrorSafeWalk(speciesId: String): Boolean = speciesId == "tiger" || speciesId == "whale" || speciesId == "sunflower"
    fun weakWalk(speciesId: String): Boolean = speciesId == "sweetpotato"
    fun drawable(id: String): DrawableResource? {
        if (id !in ids) return null
        val head = id[0]
        return if (head < 'm') poseEarly(id) else poseLate(id)
    }
    private fun poseEarly(id: String): DrawableResource = when (id) {
        "babydragon_blink" -> Res.drawable.babydragon_blink
        "babydragon_eat" -> Res.drawable.babydragon_eat
        "babydragon_happy" -> Res.drawable.babydragon_happy
        "babydragon_sleep" -> Res.drawable.babydragon_sleep
        "babydragon_walk_left" -> Res.drawable.babydragon_walk_left
        "barley_blink" -> Res.drawable.barley_blink
        "barley_eat" -> Res.drawable.barley_eat
        "barley_happy" -> Res.drawable.barley_happy
        "barley_sleep" -> Res.drawable.barley_sleep
        "barley_walk_left" -> Res.drawable.barley_walk_left
        "bear_blink" -> Res.drawable.bear_blink
        "bear_eat" -> Res.drawable.bear_eat
        "bear_happy" -> Res.drawable.bear_happy
        "bear_sleep" -> Res.drawable.bear_sleep
        "bear_walk_left" -> Res.drawable.bear_walk_left
        "butter_blink" -> Res.drawable.butter_blink
        "butter_eat" -> Res.drawable.butter_eat
        "butter_happy" -> Res.drawable.butter_happy
        "butter_sleep" -> Res.drawable.butter_sleep
        "butter_walk_left" -> Res.drawable.butter_walk_left
        "cat_blink" -> Res.drawable.cat_blink
        "cat_eat" -> Res.drawable.cat_eat
        "cat_happy" -> Res.drawable.cat_happy
        "cat_sleep" -> Res.drawable.cat_sleep
        "cat_walk_left" -> Res.drawable.cat_walk_left
        "chestnut_blink" -> Res.drawable.chestnut_blink
        "chestnut_eat" -> Res.drawable.chestnut_eat
        "chestnut_happy" -> Res.drawable.chestnut_happy
        "chestnut_sleep" -> Res.drawable.chestnut_sleep
        "chestnut_walk_left" -> Res.drawable.chestnut_walk_left
        "chick_blink" -> Res.drawable.chick_blink
        "chick_eat" -> Res.drawable.chick_eat
        "chick_happy" -> Res.drawable.chick_happy
        "chick_sleep" -> Res.drawable.chick_sleep
        "chick_walk_left" -> Res.drawable.chick_walk_left
        "cloud_blink" -> Res.drawable.cloud_blink
        "cloud_eat" -> Res.drawable.cloud_eat
        "cloud_happy" -> Res.drawable.cloud_happy
        "cloud_sleep" -> Res.drawable.cloud_sleep
        "cloud_walk_left" -> Res.drawable.cloud_walk_left
        "donut_blink" -> Res.drawable.donut_blink
        "donut_eat" -> Res.drawable.donut_eat
        "donut_happy" -> Res.drawable.donut_happy
        "donut_sleep" -> Res.drawable.donut_sleep
        "donut_walk_left" -> Res.drawable.donut_walk_left
        "duck_blink" -> Res.drawable.duck_blink
        "duck_eat" -> Res.drawable.duck_eat
        "duck_happy" -> Res.drawable.duck_happy
        "duck_sleep" -> Res.drawable.duck_sleep
        "duck_walk_left" -> Res.drawable.duck_walk_left
        "egg_blink" -> Res.drawable.egg_blink
        "egg_eat" -> Res.drawable.egg_eat
        "egg_happy" -> Res.drawable.egg_happy
        "egg_sleep" -> Res.drawable.egg_sleep
        "egg_walk_left" -> Res.drawable.egg_walk_left
        "fox_blink" -> Res.drawable.fox_blink
        "fox_eat" -> Res.drawable.fox_eat
        "fox_happy" -> Res.drawable.fox_happy
        "fox_sleep" -> Res.drawable.fox_sleep
        "fox_walk_left" -> Res.drawable.fox_walk_left
        "frog_blink" -> Res.drawable.frog_blink
        "frog_eat" -> Res.drawable.frog_eat
        "frog_happy" -> Res.drawable.frog_happy
        "frog_sleep" -> Res.drawable.frog_sleep
        "frog_walk_left" -> Res.drawable.frog_walk_left
        "ghost_blink" -> Res.drawable.ghost_blink
        "ghost_eat" -> Res.drawable.ghost_eat
        "ghost_happy" -> Res.drawable.ghost_happy
        "ghost_sleep" -> Res.drawable.ghost_sleep
        "ghost_walk_left" -> Res.drawable.ghost_walk_left
        "greentea_blink" -> Res.drawable.greentea_blink
        "greentea_eat" -> Res.drawable.greentea_eat
        "greentea_happy" -> Res.drawable.greentea_happy
        "greentea_sleep" -> Res.drawable.greentea_sleep
        "greentea_walk_left" -> Res.drawable.greentea_walk_left
        "hamster_blink" -> Res.drawable.hamster_blink
        "hamster_eat" -> Res.drawable.hamster_eat
        "hamster_happy" -> Res.drawable.hamster_happy
        "hamster_sleep" -> Res.drawable.hamster_sleep
        "hamster_walk_left" -> Res.drawable.hamster_walk_left
        "hen_blink" -> Res.drawable.hen_blink
        "hen_eat" -> Res.drawable.hen_eat
        "hen_happy" -> Res.drawable.hen_happy
        "hen_sleep" -> Res.drawable.hen_sleep
        "hen_walk_left" -> Res.drawable.hen_walk_left
        "injeolmi_blink" -> Res.drawable.injeolmi_blink
        "injeolmi_eat" -> Res.drawable.injeolmi_eat
        "injeolmi_happy" -> Res.drawable.injeolmi_happy
        "injeolmi_sleep" -> Res.drawable.injeolmi_sleep
        "injeolmi_walk_left" -> Res.drawable.injeolmi_walk_left
        "koala_blink" -> Res.drawable.koala_blink
        "koala_eat" -> Res.drawable.koala_eat
        "koala_happy" -> Res.drawable.koala_happy
        "koala_sleep" -> Res.drawable.koala_sleep
        "koala_walk_left" -> Res.drawable.koala_walk_left
        "lemon_blink" -> Res.drawable.lemon_blink
        "lemon_eat" -> Res.drawable.lemon_eat
        "lemon_happy" -> Res.drawable.lemon_happy
        "lemon_sleep" -> Res.drawable.lemon_sleep
        "lemon_walk_left" -> Res.drawable.lemon_walk_left
        "lion_blink" -> Res.drawable.lion_blink
        "lion_eat" -> Res.drawable.lion_eat
        "lion_happy" -> Res.drawable.lion_happy
        "lion_sleep" -> Res.drawable.lion_sleep
        "lion_walk_left" -> Res.drawable.lion_walk_left
        else -> Res.drawable.base_idle
    }
    private fun poseLate(id: String): DrawableResource = when (id) {
        "macaron_blink" -> Res.drawable.macaron_blink
        "macaron_eat" -> Res.drawable.macaron_eat
        "macaron_happy" -> Res.drawable.macaron_happy
        "macaron_sleep" -> Res.drawable.macaron_sleep
        "macaron_walk_left" -> Res.drawable.macaron_walk_left
        "mandu_blink" -> Res.drawable.mandu_blink
        "mandu_eat" -> Res.drawable.mandu_eat
        "mandu_happy" -> Res.drawable.mandu_happy
        "mandu_sleep" -> Res.drawable.mandu_sleep
        "mandu_walk_left" -> Res.drawable.mandu_walk_left
        "milk_blink" -> Res.drawable.milk_blink
        "milk_eat" -> Res.drawable.milk_eat
        "milk_happy" -> Res.drawable.milk_happy
        "milk_sleep" -> Res.drawable.milk_sleep
        "milk_walk_left" -> Res.drawable.milk_walk_left
        "moon_blink" -> Res.drawable.moon_blink
        "moon_eat" -> Res.drawable.moon_eat
        "moon_happy" -> Res.drawable.moon_happy
        "moon_sleep" -> Res.drawable.moon_sleep
        "moon_walk_left" -> Res.drawable.moon_walk_left
        "mushroom_blink" -> Res.drawable.mushroom_blink
        "mushroom_eat" -> Res.drawable.mushroom_eat
        "mushroom_happy" -> Res.drawable.mushroom_happy
        "mushroom_sleep" -> Res.drawable.mushroom_sleep
        "mushroom_walk_left" -> Res.drawable.mushroom_walk_left
        "octopus_blink" -> Res.drawable.octopus_blink
        "octopus_eat" -> Res.drawable.octopus_eat
        "octopus_happy" -> Res.drawable.octopus_happy
        "octopus_sleep" -> Res.drawable.octopus_sleep
        "octopus_walk_left" -> Res.drawable.octopus_walk_left
        "otter_blink" -> Res.drawable.otter_blink
        "otter_eat" -> Res.drawable.otter_eat
        "otter_happy" -> Res.drawable.otter_happy
        "otter_sleep" -> Res.drawable.otter_sleep
        "otter_walk_left" -> Res.drawable.otter_walk_left
        "owl_blink" -> Res.drawable.owl_blink
        "owl_eat" -> Res.drawable.owl_eat
        "owl_happy" -> Res.drawable.owl_happy
        "owl_sleep" -> Res.drawable.owl_sleep
        "owl_walk_left" -> Res.drawable.owl_walk_left
        "palette_blink" -> Res.drawable.palette_blink
        "palette_eat" -> Res.drawable.palette_eat
        "palette_happy" -> Res.drawable.palette_happy
        "palette_sleep" -> Res.drawable.palette_sleep
        "palette_walk_left" -> Res.drawable.palette_walk_left
        "panda_blink" -> Res.drawable.panda_blink
        "panda_eat" -> Res.drawable.panda_eat
        "panda_happy" -> Res.drawable.panda_happy
        "panda_sleep" -> Res.drawable.panda_sleep
        "panda_walk_left" -> Res.drawable.panda_walk_left
        "peach_blink" -> Res.drawable.peach_blink
        "peach_eat" -> Res.drawable.peach_eat
        "peach_happy" -> Res.drawable.peach_happy
        "peach_sleep" -> Res.drawable.peach_sleep
        "peach_walk_left" -> Res.drawable.peach_walk_left
        "penguin_blink" -> Res.drawable.penguin_blink
        "penguin_eat" -> Res.drawable.penguin_eat
        "penguin_happy" -> Res.drawable.penguin_happy
        "penguin_sleep" -> Res.drawable.penguin_sleep
        "penguin_walk_left" -> Res.drawable.penguin_walk_left
        "pig_blink" -> Res.drawable.pig_blink
        "pig_eat" -> Res.drawable.pig_eat
        "pig_happy" -> Res.drawable.pig_happy
        "pig_sleep" -> Res.drawable.pig_sleep
        "pig_walk_left" -> Res.drawable.pig_walk_left
        "pudding_blink" -> Res.drawable.pudding_blink
        "pudding_eat" -> Res.drawable.pudding_eat
        "pudding_happy" -> Res.drawable.pudding_happy
        "pudding_sleep" -> Res.drawable.pudding_sleep
        "pudding_walk_left" -> Res.drawable.pudding_walk_left
        "puppy_blink" -> Res.drawable.puppy_blink
        "puppy_eat" -> Res.drawable.puppy_eat
        "puppy_happy" -> Res.drawable.puppy_happy
        "puppy_sleep" -> Res.drawable.puppy_sleep
        "puppy_walk_left" -> Res.drawable.puppy_walk_left
        "rabbit_blink" -> Res.drawable.rabbit_blink
        "rabbit_eat" -> Res.drawable.rabbit_eat
        "rabbit_happy" -> Res.drawable.rabbit_happy
        "rabbit_sleep" -> Res.drawable.rabbit_sleep
        "rabbit_walk_left" -> Res.drawable.rabbit_walk_left
        "rainbow_blink" -> Res.drawable.rainbow_blink
        "rainbow_eat" -> Res.drawable.rainbow_eat
        "rainbow_happy" -> Res.drawable.rainbow_happy
        "rainbow_sleep" -> Res.drawable.rainbow_sleep
        "rainbow_walk_left" -> Res.drawable.rainbow_walk_left
        "seal_blink" -> Res.drawable.seal_blink
        "seal_eat" -> Res.drawable.seal_eat
        "seal_happy" -> Res.drawable.seal_happy
        "seal_sleep" -> Res.drawable.seal_sleep
        "seal_walk_left" -> Res.drawable.seal_walk_left
        "shark_blink" -> Res.drawable.shark_blink
        "shark_eat" -> Res.drawable.shark_eat
        "shark_happy" -> Res.drawable.shark_happy
        "shark_sleep" -> Res.drawable.shark_sleep
        "shark_walk_left" -> Res.drawable.shark_walk_left
        "sheep_blink" -> Res.drawable.sheep_blink
        "sheep_eat" -> Res.drawable.sheep_eat
        "sheep_happy" -> Res.drawable.sheep_happy
        "sheep_sleep" -> Res.drawable.sheep_sleep
        "sheep_walk_left" -> Res.drawable.sheep_walk_left
        "snowman_blink" -> Res.drawable.snowman_blink
        "snowman_eat" -> Res.drawable.snowman_eat
        "snowman_happy" -> Res.drawable.snowman_happy
        "snowman_sleep" -> Res.drawable.snowman_sleep
        "snowman_walk_left" -> Res.drawable.snowman_walk_left
        "sprout_blink" -> Res.drawable.sprout_blink
        "sprout_eat" -> Res.drawable.sprout_eat
        "sprout_happy" -> Res.drawable.sprout_happy
        "sprout_sleep" -> Res.drawable.sprout_sleep
        "sprout_walk_left" -> Res.drawable.sprout_walk_left
        "squirrel_blink" -> Res.drawable.squirrel_blink
        "squirrel_eat" -> Res.drawable.squirrel_eat
        "squirrel_happy" -> Res.drawable.squirrel_happy
        "squirrel_sleep" -> Res.drawable.squirrel_sleep
        "squirrel_walk_left" -> Res.drawable.squirrel_walk_left
        "star_blink" -> Res.drawable.star_blink
        "star_eat" -> Res.drawable.star_eat
        "star_happy" -> Res.drawable.star_happy
        "star_sleep" -> Res.drawable.star_sleep
        "star_walk_left" -> Res.drawable.star_walk_left
        "strawberry_blink" -> Res.drawable.strawberry_blink
        "strawberry_eat" -> Res.drawable.strawberry_eat
        "strawberry_happy" -> Res.drawable.strawberry_happy
        "strawberry_sleep" -> Res.drawable.strawberry_sleep
        "strawberry_walk_left" -> Res.drawable.strawberry_walk_left
        "sunflower_blink" -> Res.drawable.sunflower_blink
        "sunflower_eat" -> Res.drawable.sunflower_eat
        "sunflower_happy" -> Res.drawable.sunflower_happy
        "sunflower_sleep" -> Res.drawable.sunflower_sleep
        "sunflower_walk_left" -> Res.drawable.sunflower_walk_left
        "sweetpotato_blink" -> Res.drawable.sweetpotato_blink
        "sweetpotato_eat" -> Res.drawable.sweetpotato_eat
        "sweetpotato_happy" -> Res.drawable.sweetpotato_happy
        "sweetpotato_sleep" -> Res.drawable.sweetpotato_sleep
        "sweetpotato_walk_left" -> Res.drawable.sweetpotato_walk_left
        "tiger_blink" -> Res.drawable.tiger_blink
        "tiger_eat" -> Res.drawable.tiger_eat
        "tiger_happy" -> Res.drawable.tiger_happy
        "tiger_sleep" -> Res.drawable.tiger_sleep
        "tiger_walk_left" -> Res.drawable.tiger_walk_left
        "whale_blink" -> Res.drawable.whale_blink
        "whale_eat" -> Res.drawable.whale_eat
        "whale_happy" -> Res.drawable.whale_happy
        "whale_sleep" -> Res.drawable.whale_sleep
        "whale_walk_left" -> Res.drawable.whale_walk_left
        else -> Res.drawable.base_idle
    }
    private val ids = setOf(
        "babydragon_blink",
        "babydragon_eat",
        "babydragon_happy",
        "babydragon_sleep",
        "babydragon_walk_left",
        "barley_blink",
        "barley_eat",
        "barley_happy",
        "barley_sleep",
        "barley_walk_left",
        "bear_blink",
        "bear_eat",
        "bear_happy",
        "bear_sleep",
        "bear_walk_left",
        "butter_blink",
        "butter_eat",
        "butter_happy",
        "butter_sleep",
        "butter_walk_left",
        "cat_blink",
        "cat_eat",
        "cat_happy",
        "cat_sleep",
        "cat_walk_left",
        "chestnut_blink",
        "chestnut_eat",
        "chestnut_happy",
        "chestnut_sleep",
        "chestnut_walk_left",
        "chick_blink",
        "chick_eat",
        "chick_happy",
        "chick_sleep",
        "chick_walk_left",
        "cloud_blink",
        "cloud_eat",
        "cloud_happy",
        "cloud_sleep",
        "cloud_walk_left",
        "donut_blink",
        "donut_eat",
        "donut_happy",
        "donut_sleep",
        "donut_walk_left",
        "duck_blink",
        "duck_eat",
        "duck_happy",
        "duck_sleep",
        "duck_walk_left",
        "egg_blink",
        "egg_eat",
        "egg_happy",
        "egg_sleep",
        "egg_walk_left",
        "fox_blink",
        "fox_eat",
        "fox_happy",
        "fox_sleep",
        "fox_walk_left",
        "frog_blink",
        "frog_eat",
        "frog_happy",
        "frog_sleep",
        "frog_walk_left",
        "ghost_blink",
        "ghost_eat",
        "ghost_happy",
        "ghost_sleep",
        "ghost_walk_left",
        "greentea_blink",
        "greentea_eat",
        "greentea_happy",
        "greentea_sleep",
        "greentea_walk_left",
        "hamster_blink",
        "hamster_eat",
        "hamster_happy",
        "hamster_sleep",
        "hamster_walk_left",
        "hen_blink",
        "hen_eat",
        "hen_happy",
        "hen_sleep",
        "hen_walk_left",
        "injeolmi_blink",
        "injeolmi_eat",
        "injeolmi_happy",
        "injeolmi_sleep",
        "injeolmi_walk_left",
        "koala_blink",
        "koala_eat",
        "koala_happy",
        "koala_sleep",
        "koala_walk_left",
        "lemon_blink",
        "lemon_eat",
        "lemon_happy",
        "lemon_sleep",
        "lemon_walk_left",
        "lion_blink",
        "lion_eat",
        "lion_happy",
        "lion_sleep",
        "lion_walk_left",
        "macaron_blink",
        "macaron_eat",
        "macaron_happy",
        "macaron_sleep",
        "macaron_walk_left",
        "mandu_blink",
        "mandu_eat",
        "mandu_happy",
        "mandu_sleep",
        "mandu_walk_left",
        "milk_blink",
        "milk_eat",
        "milk_happy",
        "milk_sleep",
        "milk_walk_left",
        "moon_blink",
        "moon_eat",
        "moon_happy",
        "moon_sleep",
        "moon_walk_left",
        "mushroom_blink",
        "mushroom_eat",
        "mushroom_happy",
        "mushroom_sleep",
        "mushroom_walk_left",
        "octopus_blink",
        "octopus_eat",
        "octopus_happy",
        "octopus_sleep",
        "octopus_walk_left",
        "otter_blink",
        "otter_eat",
        "otter_happy",
        "otter_sleep",
        "otter_walk_left",
        "owl_blink",
        "owl_eat",
        "owl_happy",
        "owl_sleep",
        "owl_walk_left",
        "palette_blink",
        "palette_eat",
        "palette_happy",
        "palette_sleep",
        "palette_walk_left",
        "panda_blink",
        "panda_eat",
        "panda_happy",
        "panda_sleep",
        "panda_walk_left",
        "peach_blink",
        "peach_eat",
        "peach_happy",
        "peach_sleep",
        "peach_walk_left",
        "penguin_blink",
        "penguin_eat",
        "penguin_happy",
        "penguin_sleep",
        "penguin_walk_left",
        "pig_blink",
        "pig_eat",
        "pig_happy",
        "pig_sleep",
        "pig_walk_left",
        "pudding_blink",
        "pudding_eat",
        "pudding_happy",
        "pudding_sleep",
        "pudding_walk_left",
        "puppy_blink",
        "puppy_eat",
        "puppy_happy",
        "puppy_sleep",
        "puppy_walk_left",
        "rabbit_blink",
        "rabbit_eat",
        "rabbit_happy",
        "rabbit_sleep",
        "rabbit_walk_left",
        "rainbow_blink",
        "rainbow_eat",
        "rainbow_happy",
        "rainbow_sleep",
        "rainbow_walk_left",
        "seal_blink",
        "seal_eat",
        "seal_happy",
        "seal_sleep",
        "seal_walk_left",
        "shark_blink",
        "shark_eat",
        "shark_happy",
        "shark_sleep",
        "shark_walk_left",
        "sheep_blink",
        "sheep_eat",
        "sheep_happy",
        "sheep_sleep",
        "sheep_walk_left",
        "snowman_blink",
        "snowman_eat",
        "snowman_happy",
        "snowman_sleep",
        "snowman_walk_left",
        "sprout_blink",
        "sprout_eat",
        "sprout_happy",
        "sprout_sleep",
        "sprout_walk_left",
        "squirrel_blink",
        "squirrel_eat",
        "squirrel_happy",
        "squirrel_sleep",
        "squirrel_walk_left",
        "star_blink",
        "star_eat",
        "star_happy",
        "star_sleep",
        "star_walk_left",
        "strawberry_blink",
        "strawberry_eat",
        "strawberry_happy",
        "strawberry_sleep",
        "strawberry_walk_left",
        "sunflower_blink",
        "sunflower_eat",
        "sunflower_happy",
        "sunflower_sleep",
        "sunflower_walk_left",
        "sweetpotato_blink",
        "sweetpotato_eat",
        "sweetpotato_happy",
        "sweetpotato_sleep",
        "sweetpotato_walk_left",
        "tiger_blink",
        "tiger_eat",
        "tiger_happy",
        "tiger_sleep",
        "tiger_walk_left",
        "whale_blink",
        "whale_eat",
        "whale_happy",
        "whale_sleep",
        "whale_walk_left",
    )
}
