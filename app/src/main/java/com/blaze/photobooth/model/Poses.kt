package com.blaze.photobooth.model

import androidx.annotation.StringRes
import com.blaze.photobooth.R

/** Who a pose is written for (README §5.6.2). Solo poses also work for two or more. */
enum class Who { Solo, Duo, Squad }

/** A pose's feel; a strip never has two of the same in a row (§5.6.1). */
enum class Mood { Cute, Silly, Cool, Love }

/**
 * One pose idea (§5.6.2): a stable [id] (saved in `draft.json`'s pose plan), an [emoji], tags, and its words in
 * `strings.xml` (`pose_<id>_name` and the optional Korean `pose_<id>_ko`, with `-` written as `_`).
 */
data class Pose(
    val id: String,
    val emoji: String,
    val who: Who,
    val mood: Mood,
    @param:StringRes val name: Int,
    @param:StringRes val korean: Int?,
)

/** The pose catalogue (§5.6.2, 55 poses), in the README's order. Adding a pose = a line here + its strings. */
object Poses {
    val all: List<Pose> = listOf(
        // Solo, also fine for everyone together (29)
        Pose("smile", "😁", Who.Solo, Mood.Cute, R.string.pose_smile_name, null),
        Pose("cheek-heart", "🫶", Who.Solo, Mood.Cute, R.string.pose_cheek_heart_name, R.string.pose_cheek_heart_ko),
        Pose("finger-heart", "🫰", Who.Solo, Mood.Love, R.string.pose_finger_heart_name, R.string.pose_finger_heart_ko),
        Pose("chin-v", "✌️", Who.Solo, Mood.Cute, R.string.pose_chin_v_name, R.string.pose_chin_v_ko),
        Pose("flower-cup", "🌸", Who.Solo, Mood.Cute, R.string.pose_flower_cup_name, R.string.pose_flower_cup_ko),
        Pose("puffy-cheeks", "🐡", Who.Solo, Mood.Silly, R.string.pose_puffy_cheeks_name, R.string.pose_puffy_cheeks_ko),
        Pose("cat-paws", "🐱", Who.Solo, Mood.Cute, R.string.pose_cat_paws_name, R.string.pose_cat_paws_ko),
        Pose("bunny-ears", "🐰", Who.Solo, Mood.Cute, R.string.pose_bunny_ears_name, null),
        Pose("head-heart", "💗", Who.Solo, Mood.Love, R.string.pose_head_heart_name, R.string.pose_head_heart_ko),
        Pose("blow-kiss", "😘", Who.Solo, Mood.Love, R.string.pose_blow_kiss_name, null),
        Pose("wink", "😉", Who.Solo, Mood.Cool, R.string.pose_wink_name, null),
        Pose("surprised", "😮", Who.Solo, Mood.Silly, R.string.pose_surprised_name, null),
        Pose("thinking", "🤔", Who.Solo, Mood.Silly, R.string.pose_thinking_name, null),
        Pose("hand-glasses", "😎", Who.Solo, Mood.Cool, R.string.pose_hand_glasses_name, null),
        Pose("ok-eye", "👌", Who.Solo, Mood.Cool, R.string.pose_ok_eye_name, null),
        Pose("point-camera", "👉", Who.Solo, Mood.Cool, R.string.pose_point_camera_name, null),
        Pose("laugh", "😂", Who.Solo, Mood.Silly, R.string.pose_laugh_name, null),
        Pose("grumpy", "😤", Who.Solo, Mood.Silly, R.string.pose_grumpy_name, null),
        Pose("sleepy", "😴", Who.Solo, Mood.Silly, R.string.pose_sleepy_name, null),
        Pose("chin-rest", "🙂", Who.Solo, Mood.Cute, R.string.pose_chin_rest_name, R.string.pose_chin_rest_ko),
        Pose("peek", "🙈", Who.Solo, Mood.Cute, R.string.pose_peek_name, R.string.pose_peek_ko),
        Pose("tongue", "😛", Who.Solo, Mood.Silly, R.string.pose_tongue_name, null),
        Pose("idol-fist", "✊", Who.Solo, Mood.Cool, R.string.pose_idol_fist_name, null),
        Pose("hair-flip", "💁", Who.Solo, Mood.Cool, R.string.pose_hair_flip_name, null),
        Pose("aegyo", "✨", Who.Solo, Mood.Cute, R.string.pose_aegyo_name, R.string.pose_aegyo_ko),
        // Added after v1.2 so Just me has more love poses for the strip's last cut (§5.6.2).
        Pose("heart-eyes", "😍", Who.Solo, Mood.Love, R.string.pose_heart_eyes_name, null),
        Pose("self-hug", "🤗", Who.Solo, Mood.Love, R.string.pose_self_hug_name, null),
        Pose("chest-heart", "💓", Who.Solo, Mood.Love, R.string.pose_chest_heart_name, null),
        Pose("face-heart", "💝", Who.Solo, Mood.Love, R.string.pose_face_heart_name, null),
        // Two of us (14)
        Pose("shared-heart", "💕", Who.Duo, Mood.Love, R.string.pose_shared_heart_name, R.string.pose_shared_heart_ko),
        Pose("cheek-to-cheek", "🥰", Who.Duo, Mood.Love, R.string.pose_cheek_to_cheek_name, null),
        Pose("back-to-back", "😎", Who.Duo, Mood.Cool, R.string.pose_back_to_back_name, null),
        Pose("foreheads", "💞", Who.Duo, Mood.Love, R.string.pose_foreheads_name, null),
        Pose("cheek-kiss", "😚", Who.Duo, Mood.Love, R.string.pose_cheek_kiss_name, R.string.pose_cheek_kiss_ko),
        Pose("cheek-pinch", "🤏", Who.Duo, Mood.Silly, R.string.pose_cheek_pinch_name, null),
        Pose("secret", "🤫", Who.Duo, Mood.Silly, R.string.pose_secret_name, null),
        Pose("back-hug", "🤗", Who.Duo, Mood.Love, R.string.pose_back_hug_name, R.string.pose_back_hug_ko),
        Pose("bunny-swap", "🐰", Who.Duo, Mood.Silly, R.string.pose_bunny_swap_name, null),
        Pose("pretend-fight", "🥊", Who.Duo, Mood.Silly, R.string.pose_pretend_fight_name, null),
        Pose("point-each", "👉👈", Who.Duo, Mood.Cute, R.string.pose_point_each_name, null),
        Pose("mirror", "🪞", Who.Duo, Mood.Cool, R.string.pose_mirror_name, null),
        Pose("face-squish", "🫠", Who.Duo, Mood.Silly, R.string.pose_face_squish_name, null),
        Pose("gaze", "👀", Who.Duo, Mood.Love, R.string.pose_gaze_name, null),
        // Squad, 3+ (12)
        Pose("squish-in", "🤭", Who.Squad, Mood.Cute, R.string.pose_squish_in_name, null),
        Pose("size-order", "📏", Who.Squad, Mood.Silly, R.string.pose_size_order_name, null),
        Pose("pyramid", "🔺", Who.Squad, Mood.Silly, R.string.pose_pyramid_name, null),
        Pose("group-heart", "💖", Who.Squad, Mood.Love, R.string.pose_group_heart_name, null),
        Pose("point-one", "👇", Who.Squad, Mood.Silly, R.string.pose_point_one_name, null),
        Pose("look-up", "⬆️", Who.Squad, Mood.Cool, R.string.pose_look_up_name, null),
        Pose("same-pose", "🪄", Who.Squad, Mood.Silly, R.string.pose_same_pose_name, null),
        Pose("totem", "🗿", Who.Squad, Mood.Silly, R.string.pose_totem_name, null),
        Pose("gasp", "😱", Who.Squad, Mood.Silly, R.string.pose_gasp_name, null),
        Pose("finger-hearts", "🫰", Who.Squad, Mood.Love, R.string.pose_finger_hearts_name, null),
        Pose("hug-sandwich", "🥪", Who.Squad, Mood.Love, R.string.pose_hug_sandwich_name, null),
        Pose("wave", "👋", Who.Squad, Mood.Cute, R.string.pose_wave_name, null),
    )

    private val byId: Map<String, Pose> = all.associateBy { it.id }

    fun find(id: String?): Pose? = id?.let(byId::get)

    /**
     * The poses that suit [crowd]: the solo list for one person; for two or a squad, their own list plus the solo
     * list (solo poses work for everyone together). [PosePlanner] leans ~60% toward the crowd's own poses.
     */
    fun poolFor(crowd: Crowd, catalogue: List<Pose> = all): List<Pose> = catalogue.filter { it.who == Who.Solo || it.who == crowd.who }

    /** The crowd's own pose list (for Just me, the solo list itself). */
    val Crowd.who: Who
        get() = when (this) {
            Crowd.Solo -> Who.Solo
            Crowd.Duo -> Who.Duo
            Crowd.Squad -> Who.Squad
        }
}
