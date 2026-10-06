package com.example.chrono

enum class Lang { FR, EN, ZH }

/** Toutes les chaînes de l'appli, en français / anglais / chinois simplifié. */
enum class S(val fr: String, val en: String, val zh: String) {
    LAP("Tour", "Lap", "计圈"),
    RESET("Reset", "Reset", "重置"),
    START("Start", "Start", "开始"),
    STOP("Stop", "Stop", "停止"),
    UNDO("Annuler", "Undo", "撤销"),
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
    TOUCH_RING("Bague tactile (secours)", "Touch bezel (backup)", "触摸转环（备用）"),
    LEFTY("Mode gaucher", "Left-handed", "左手模式"),
    UNDO_BTN("Bouton annuler tour", "Undo-lap button", "撤销按钮"),
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
    NEVER("Jamais", "Never", "从不"),
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

    NOTIF_TITLE("Chronomètre", "Stopwatch", "秒表"),
    NOTIF_CHANNEL("Chrono en cours", "Stopwatch running", "秒表运行中"),
    NOTIF_STOP_RESET("Stop + Reset", "Stop + Reset", "停止并重置"),
    COMP_DESC("Chronomètre", "Stopwatch", "秒表"),

    LOGO("Logo", "Logo", "图标"),
    RING_EV("Événements par ligne", "Events per line", "每行事件数"),
    AUTO_SCROLL("Défilement auto", "Auto-scroll", "自动滚动"),
    RUN_ICONS("Réglages en activité", "Settings while running", "运行中显示设置"),
    HELP_TOGGLE("Aide", "Help", "帮助"),
    TRACK("Suivi avancé", "Advanced tracking", "高级跟踪"),
    TRACK_PRECISION("Précision distance", "Distance precision", "距离精度"),
    TRACK_DIST("Distance par tour", "Distance per lap", "每圈距离"),
    EX_TIME("Temps d'exercice", "Exercise time", "运动时间"),
    EX_REPS("Répétitions", "Repetitions", "重复次数"),
    REST_TIME("Temps de repos", "Rest time", "休息时间"),
    COL_PACE("Allure", "Pace", "配速"),
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
        "L'affichage permanent garde une image fixe allumée : risque de marquage de l'écran, et batterie consommée plus vite.",
        "Always-on display keeps a static image lit: risk of screen burn-in and faster battery drain.",
        "息屏显示会持续点亮静态画面：可能造成烧屏，并增加耗电。"
    ),
    WARN_OK("Activer", "Enable", "启用"),
    WARN_CANCEL("Annuler", "Cancel", "取消"),
    HELP_TITLE("Aide", "Help", "帮助"),
    HELP_TEXT(
        "• Tour / Start : cercles ou bouton du bas\n• Reset, effacer, cadenas : maintenir\n• Curseur : appui long = plage min/max\n• Tours : bague rotative\n• Icône réglages : maintenir en activité",
        "• Lap / Start: buttons or bottom key\n• Reset, clear, padlock: hold\n• Slider: long press = min/max range\n• Laps: rotary bezel\n• Settings icon: hold while running",
        "• 计圈/开始：按钮或下方按键\n• 重置、清除、锁：长按\n• 滑块：长按=设置范围\n• 圈次：旋转表圈\n• 运行中：长按设置图标"
    ),
    ABOUT_BY(
        "Raphaël GARIVIER avec Claude · Octobre 2026",
        "Raphaël GARIVIER with Claude · October 2026",
        "Raphaël GARIVIER 与 Claude · 2026年10月"
    )
}
fun S.t(): String = when (Settings.lang) {
    Lang.FR -> fr
    Lang.EN -> en
    Lang.ZH -> zh
}
