package com.example.chrono

enum class Lang { FR, EN, ZH }

/** Toutes les chaînes de l'appli, en français / anglais / chinois simplifié. */
enum class S(val fr: String, val en: String, val zh: String) {
    LAP("Tour", "Lap", "计圈"),
    RESET("Reset", "Reset", "重置"),
    START("Start", "Start", "开始"),
    STOP("Stop", "Stop", "停止"),
    UNDO("Annuler", "Undo", "撤销"),
    FINISH("Terminer", "Finish", "完成"),
    DELETE_SHORT("Suppr.", "Delete", "删除"),
    AVG("Moy.", "Avg.", "平均"),

    SETTINGS("Réglages", "Settings", "设置"),
    RING("Cercle", "Ring", "圆环"),
    AOD("Always-on display", "Always-on display", "息屏显示"),
    ECO("Mode éco", "Eco mode", "省电模式"),
    ECO_BRIGHT("Luminosité éco", "Eco brightness", "省电亮度"),
    LOCK("Verrou tactile", "Touch lock", "触屏锁定"),
    AUTO_UNLOCK("Déverrouillage auto", "Auto unlock", "自动解锁"),
    RING_IN_LOCK("Bague en verrou", "Bezel when locked", "锁定时可转环"),
    CUSTOM("Personnalisation", "Customize", "个性化"),
    LANGUAGE("Langue", "Language", "语言"),
    LIGHT("Mode clair", "Light mode", "浅色模式"),
    SNAKE("Taille du snake", "Snake size", "光带长度"),
    LEFTY("Mode gaucher", "Left-handed", "左手模式"),
    UNDO_BTN("Bouton annuler / suppr.", "Undo / delete button", "撤销 / 删除按钮"),
    SECONDS("Temps en secondes", "Time in seconds", "以秒显示"),
    FADE("Fondu des tours", "List fade", "列表渐隐"),
    COLS("Colonnes des tours", "Lap columns", "圈次列"),
    COL_NUM("N°", "No.", "序号"),
    COL_TOTAL("Total", "Total", "总计"),
    COL_LAP("Tour", "Lap", "单圈"),
    COL_DELTA("Écart", "Delta", "差值"),
    SIZES("Tailles", "Sizes", "大小"),
    S_CHRONO("Chrono", "Timer", "秒表"),
    S_BTN("Cercles", "Buttons", "按钮"),
    S_LAPS("Tours", "Laps", "圈次"),
    SPACING("Espacements (% de la hauteur)", "Spacing (% of height)", "间距（占屏高 %）"),
    GAP_TIME_BTN("Chrono → cercles", "Timer → buttons", "秒表→按钮"),
    GAP_BTN("Entre les cercles", "Between buttons", "按钮之间"),
    GAP_BTN_LAP("Cercles → tours", "Buttons → laps", "按钮→圈次"),
    GAP_LINES("Entre les tours", "Between laps", "圈次之间"),
    LAYOUT("Disposition", "Layout", "布局"),
    TOP_PCT("Haut de l'écran", "Top of screen", "上方占比"),
    LAP_WIDTH("Largeur des tours", "Lap width", "圈次宽度"),
    LAYOUT_RESET("Mise en page par défaut (maintenir)", "Default layout (hold)", "恢复默认布局（长按）"),
    BEHAVIOR("Comportement", "Behavior", "行为"),
    HOLD("Temps de maintien", "Hold time", "长按时间"),
    VIBE("Vibrations", "Vibration", "振动"),
    NONE("Aucune", "None", "无"),
    AUTO_STOP("Arrêt auto du chrono", "Auto stop", "自动停止"),
    OFF("Désactivé", "Off", "关闭"),
    COLOR("Couleur", "Color", "颜色"),
    RED("Rouge", "Red", "红"),
    GREEN("Vert", "Green", "绿"),
    BLUE("Bleu", "Blue", "蓝"),

    HISTORY("Historique", "History", "历史记录"),
    NO_SESSION("Aucune séance", "No sessions", "暂无记录"),
    LAP_ONE("tour", "lap", "圈"),
    LAP_MANY("tours", "laps", "圈"),
    CLEAR_ALL("Tout effacer (maintenir)", "Clear all (hold)", "全部清除（长按）"),
    NOT_FOUND("Séance introuvable", "Session not found", "未找到记录"),
    NO_LAP("Aucun tour", "No laps", "无圈次"),

    RANGE("Plage", "Range", "范围"),
    MIN("Min", "Min", "最小"),
    MAX("Max", "Max", "最大"),
    RANGE_RESET("Plage d'origine (maintenir)", "Original range (hold)", "恢复原始范围（长按）"),
    LOG_SCALE("Échelle log", "Log scale", "对数刻度"),

    NOTIF_TITLE("Chronomètre", "Stopwatch", "秒表"),
    NOTIF_CHANNEL("Chrono en cours", "Stopwatch running", "秒表运行中"),
    NOTIF_STOP_RESET("Stop + Reset", "Stop + Reset", "停止并重置"),
    COMP_DESC("Chronomètre", "Stopwatch", "秒表"),

    LOGO("Logo", "Logo", "图标"),
    AOD_DELAY("Délai avant veille", "Dim delay", "息屏延迟"),
    MARKERS("Triangles ▲▼", "Triangles ▲▼", "三角标记 ▲▼"),
    TRACK_VIBE("Vibrations des objectifs", "Goal vibrations", "目标振动"),
    AUTO_SCROLL("Défilement auto", "Auto-scroll", "自动滚动"),
    RUN_ICONS("Réglages en activité", "Settings while running", "运行中显示设置"),
    HELP_TOGGLE("Aide", "Help", "帮助"),
    TRACK("Suivi avancé", "Advanced tracking", "高级跟踪"),
    TRACK_PRECISION("Précision distance", "Distance precision", "距离精度"),
    TRACK_PRECISION_TIME("Précision temps", "Time precision", "时间精度"),
    TRACK_RECAL("Recalage des objectifs", "Goal re-sync", "目标校准"),
    TRACK_DIST("Distance par tour", "Distance per lap", "每圈距离"),
    EX_TIME("Temps d'exercice", "Exercise time", "运动时间"),
    EX_REPS("Répétitions", "Repetitions", "重复次数"),
    REST_TIME("Temps de repos", "Rest time", "休息时间"),
    COL_PACE("Allure", "Pace", "配速"),
    RAND_START("Départ random", "Random start", "随机起跑"),
    RAND_MIN("Attente min", "Min wait", "最短等待"),
    RAND_MAX("Attente max", "Max wait", "最长等待"),
    RAND_VOL("Volume du départ", "Start volume", "起跑音量"),
    RAND_LAT("Compensation audio", "Audio offset", "音频补偿"),
    CALIB_REF("Réaction de référence", "Reference reaction", "参考反应时间"),
    RAND_FREEZE("Chrono figé pendant l'attente", "Timer frozen while waiting", "等待时秒表暂停"),
    RAND_MARKS("À vos marques", "On your marks", "各就位"),
    RAND_READY("Prêts", "Set", "预备"),
    RAND_SHOT("Coup de feu", "Gun shot", "发令枪"),
    RS_MARKS_TXT("À vos marques…", "On your marks…", "各就位…"),
    RS_READY_TXT("Prêts…", "Set…", "预备…"),
    RS_NOTIF("Départ random…", "Random start…", "随机起跑…"),
    QR_SECTION("Codes QR", "QR codes", "二维码"),
    QR_VERSION("Version QR", "QR version", "二维码版本"),
    QR_EC("Correction QR", "QR correction", "纠错等级"),
    QR_LAPS("tours max", "laps max", "圈上限"),
    DELETE_ONE("Supprimer cette séance (maintenir)", "Delete this session (hold)", "删除此记录（长按）"),
    BEST("Meilleur", "Best", "最佳"),
    WORST("Pire", "Worst", "最差"),
    MEDIAN("Médiane", "Median", "中位数"),
    STDEV("Écart-type", "Std dev", "标准差"),
    REGULARITY("Régularité", "Consistency", "稳定度"),
    WARN_TITLE("ATTENTION", "WARNING", "注意"),
    WARN_LIGHT(
        "Le mode clair allume beaucoup de pixels : risque de marquage et d'usure de l'écran AMOLED, et batterie consommée bien plus vite.",
        "Light mode lights up many pixels: risk of burn-in and wear on the AMOLED screen, and much faster battery drain.",
        "浅色模式会点亮大量像素：可能造成AMOLED屏幕烧屏和老化，并大幅增加耗电。"
    ),
    WARN_AOD(
        "L'écran reste allumé tant que le chrono tourne (veille douce : noir, chiffres gris, luminosité minimale). Risque de marquage de l'écran AMOLED, et batterie consommée plus vite.",
        "The screen stays on while the timer runs (soft dim: black, gray digits, minimum brightness). Risk of AMOLED burn-in and faster battery drain.",
        "秒表运行期间屏幕将保持常亮（柔和待机：黑底灰字、最低亮度）。可能造成 AMOLED 烧屏，并加快耗电。"
    ),
    WARN_RUNICONS(
        "Sans cette option, les réglages et le suivi sont masqués dès que le chrono tourne ou affiche un temps supérieur à 0. Un paramètre mal réglé (verrou, taille, couleur…) ne pourra plus être corrigé avant la remise à zéro : l'appli peut sembler bloquée.",
        "Without this option, settings and tracking icons are hidden as soon as the timer runs or shows a time above 0. A badly set parameter (lock, size, color…) can't be fixed until you reset: the app may seem stuck.",
        "关闭后，只要秒表在运行或显示的时间大于 0，设置和跟踪图标就会隐藏。若某项参数设置不当（锁定、大小、颜色等），在重置之前无法修改，应用可能看起来卡住。"
    ),
    WARN_DISABLE("Désactiver", "Disable", "关闭"),
    WARN_OK("Activer", "Enable", "启用"),
    WARN_CANCEL("Annuler", "Cancel", "取消"),
    HELP_TITLE("Aide", "Help", "帮助"),
    CALIB_SECTION("Calibrage audio", "Audio calibration", "音频校准"),
    CALIB_LAUNCH("LANCER LE CALIBRAGE", "START CALIBRATION", "开始校准"),
    CALIB_N("Nombre de tests", "Number of tests", "测试次数"),
    CALIB_STAT("Médiane ou moyenne", "Median or mean", "中位数或平均值"),
    MEAN("Moyenne", "Mean", "平均值"),
    CALIB_INTRO(
        "À chaque coup de feu, appuie le plus vite possible sur le bouton du bas ou touche l'écran. Pas de vibration : réagis au son. N'appuie pas avant.",
        "At each gun shot, press the bottom button or touch the screen as fast as you can. No vibration: react to the sound. Do not press early.",
        "每次枪响时，尽快按底部按键或触摸屏幕。不振动：请根据声音反应。不要提前按。"
    ),
    CALIB_GO("Commencer", "Begin", "开始测试"),
    CALIB_AGAIN("Relancer", "Run again", "重新测试"),
    CALIB_QUIT("Quitter", "Quit", "退出"),
    CALIB_READY("Prêt…", "Ready…", "准备…"),
    CALIB_TEST("Test", "Test", "测试"),
    CALIB_HINT("Bouton du bas ou écran", "Bottom button or screen", "底部按键或屏幕"),
    CALIB_EARLY("Trop tôt : on recommence", "Too early: try again", "按早了：本次重来"),
    CALIB_BUSY_RUN(
        "Impossible : le chrono tourne. Arrête-le d'abord.",
        "Not possible: the timer is running. Stop it first.",
        "无法进行：秒表正在运行，请先停止。"
    ),
    CALIB_BUSY_RAND(
        "Impossible : un départ random est en cours.",
        "Not possible: a random start is in progress.",
        "无法进行：随机起跑正在进行中。"
    ),
    CALIB_VOL0(
        "Volume du départ à 0 : rien ne serait audible. Monte-le (Suivi avancé, Départ random activé).",
        "Start volume is 0: nothing would be audible. Raise it (Advanced tracking, Random start on).",
        "起跑音量为 0：将听不到任何声音。请调高（高级跟踪，开启随机起跑）。"
    ),
    CALIB_SOUND_ERR("Impossible de lire le coup de feu.", "Could not play the gun shot.", "无法播放枪声。"),
    CALIB_TIMEOUT(
        "Aucune réaction : calibrage arrêté, rien n'a été modifié.",
        "No reaction: calibration stopped, nothing was changed.",
        "未检测到反应：校准已停止，未做任何更改。"
    ),
    CALIB_CANCELLED(
        "Calibrage annulé : rien n'a été modifié.",
        "Calibration cancelled: nothing was changed.",
        "校准已取消：未做任何更改。"
    ),
    CALIB_APPLIED(
        "Appliquée automatiquement au réglage « Compensation audio ».",
        "Applied automatically to the « Audio offset » setting.",
        "已自动应用到“音频补偿”设置。"
    ),
    CALIB_CLAMP_LOW(
        "Réaction sous la référence : compensation bornée à 0.",
        "Reaction below the reference: offset limited to 0.",
        "反应时间低于参考值：补偿值限定为 0。"
    ),
    CALIB_CLAMP_HIGH(
        "Résultat borné au maximum (plus longue piste audio).",
        "Result limited to the maximum (longest audio track).",
        "结果限定为最大值（最长音频时长）。"
    ),
    CALIB_REACTIONS("Réactions", "Reactions", "各次反应时间"),
    ABOUT_BY(
        "Raphaël GARIVIER avec Claude · Novembre 2026",
        "Raphaël GARIVIER with Claude · November 2026",
        "Raphaël GARIVIER 与 Claude · 2026年11月"
    )
}
fun S.t(): String = when (Settings.lang) {
    Lang.FR -> fr
    Lang.EN -> en
    Lang.ZH -> zh
}
