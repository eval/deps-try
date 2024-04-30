(ns eval.deps-try.ansi-escape
  "Source: https://github.com/strojure/ansi-escape
  ANSI color escape sequences.
  See https://stackoverflow.com/questions/4842424/list-of-ansi-color-escape-sequences.")

(set! *warn-on-reflection* true)

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

;;; Font effects.

(def ^:const reset "Reset/Normal." "\033[0m")

(def ^:const bold,,,,, "Bold or increased intensity." "\033[1m")
(def ^:const underline "Underline.",,,,,,,,,,,,,,,,,, "\033[4m")

;;; 4-bit colors.

(def ^:const fg-black,,,, "Black FG color.",,,,,,,,, "\033[30m")
(def ^:const fg-black-b,, "Bright Black FG color.",, "\033[90m")
(def ^:const fg-red,,,,,, "Red FG color.",,,,,,,,,,, "\033[31m")
(def ^:const fg-red-b,,,, "Bright Red FG color.",,,, "\033[91m")
(def ^:const fg-green,,,, "Green FG color.",,,,,,,,, "\033[32m")
(def ^:const fg-green-b,, "Bright Green FG color.",, "\033[92m")
(def ^:const fg-yellow,,, "Yellow FG color.",,,,,,,, "\033[33m")
(def ^:const fg-yellow-b, "Bright Yellow FG color.", "\033[93m")
(def ^:const fg-blue,,,,, "Blue FG color.",,,,,,,,,, "\033[34m")
(def ^:const fg-blue-b,,, "Bright Blue FG color.",,, "\033[94m")
(def ^:const fg-magenta,, "Magenta FG color.",,,,,,, "\033[35m")
(def ^:const fg-magenta-b "Bright Magenta FG color." "\033[95m")
(def ^:const fg-cyan,,,,, "Cyan FG color.",,,,,,,,,, "\033[36m")
(def ^:const fg-cyan-b,,, "Bright Cyan FG color.",,, "\033[96m")
(def ^:const fg-white,,,, "White FG color.",,,,,,,,, "\033[37m")
(def ^:const fg-white-b,, "Bright White FG color.",, "\033[97m")

(def ^:const bg-black,,,, "Black BG color.",,,,,,,,, "\033[40m")
(def ^:const bg-black-b,, "Bright Black BG color.",, "\033[100m")
(def ^:const bg-red,,,,,, "Red BG color.",,,,,,,,,,, "\033[41m")
(def ^:const bg-red-b,,,, "Bright Red BG color.",,,, "\033[101m")
(def ^:const bg-green,,,, "Green BG color.",,,,,,,,, "\033[42m")
(def ^:const bg-green-b,, "Bright Green BG color.",, "\033[102m")
(def ^:const bg-yellow,,, "Yellow BG color.",,,,,,,, "\033[43m")
(def ^:const bg-yellow-b, "Bright Yellow BG color.", "\033[103m")
(def ^:const bg-blue,,,,, "Blue BG color.",,,,,,,,,, "\033[44m")
(def ^:const bg-blue-b,,, "Bright Blue BG color.",,, "\033[104m")
(def ^:const bg-magenta,, "Magenta BG color.",,,,,,, "\033[45m")
(def ^:const bg-magenta-b "Bright Magenta BG color." "\033[105m")
(def ^:const bg-cyan,,,,, "Cyan BG color.",,,,,,,,,, "\033[46m")
(def ^:const bg-cyan-b,,, "Bright Cyan BG color.",,, "\033[106m")
(def ^:const bg-white,,,, "White BG color.",,,,,,,,, "\033[47m")
(def ^:const bg-white-b,, "Bright White BG color.",, "\033[107m")

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••
