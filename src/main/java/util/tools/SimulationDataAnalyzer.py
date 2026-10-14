from __future__ import annotations

import json
import os
import re
import shutil
import sys
import tempfile
from copy import deepcopy

_CLI_REQUESTED = any(
    arg == "--cli"
    or arg == "--config"
    or arg.startswith("--config=")
    or arg == "--settings-json"
    or arg.startswith("--settings-json=")
    or arg == "--input"
    or arg.startswith("--input=")
    or arg == "--list-metrics"
    for arg in sys.argv[1:]
)

if _CLI_REQUESTED:
    import matplotlib
    matplotlib.use("Agg")

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from scipy.stats import t

try:
    import tkinter as tk
    from tkinter import colorchooser, filedialog, messagebox, ttk
    _TK_AVAILABLE = True
except Exception:
    # Allows CLI/headless mode to run even when Tk is not installed in the
    # container. GUI mode still requires Tkinter.
    _TK_AVAILABLE = False

    class _DummyFrame:
        def __init__(self, *args, **kwargs):
            pass

    class _DummyTtk:
        Frame = _DummyFrame

    class _DummyTkModule:
        END = "end"

    class _DummyDialog:
        @staticmethod
        def showerror(*args, **kwargs):
            raise RuntimeError(args[1] if len(args) > 1 else "Tkinter is not available.")

        @staticmethod
        def showwarning(*args, **kwargs):
            print(args[1] if len(args) > 1 else "Warning", file=sys.stderr)

    tk = _DummyTkModule()
    ttk = _DummyTtk()
    colorchooser = filedialog = messagebox = _DummyDialog()


# ======================================================
# Global configuration
# ======================================================
COMPONENT_KEYWORDS = [
    "lack of transmitters",
    "lack of receivers",
    "fragmentation",
    "QoTN",
    "QoTO",
    "crosstalk in other",
    "crosstalk",
    "other",
]

COMPONENT_LABEL_DEFAULTS = {
    "en": {
        "lack of transmitters": "Transmitters",
        "lack of receivers": "Receivers",
        "fragmentation": "Fragmentation",
        "QoTN": "OSNRN",
        "QoTO": "OSNRO",
        "crosstalk": "XTN",
        "crosstalk in other": "XTO",
        "other": "Other",
    },
    "pt": {
        "lack of transmitters": "Transmissores",
        "lack of receivers": "Receptores",
        "fragmentation": "Fragmentação",
        "QoTN": "OSNRN",
        "QoTO": "OSNRO",
        "crosstalk": "XTN",
        "crosstalk in other": "XTO",
        "other": "Outros",
    },
}

PERCENT_COMPONENT_LABEL_DEFAULTS = {
    "en": {
        "lack of transmitters": "Tx",
        "lack of receivers": "Rx",
        "fragmentation": "Frag",
        "QoTN": "OSNRN",
        "QoTO": "OSNRO",
        "crosstalk": "XTN",
        "crosstalk in other": "XTO",
        "other": "Other",
    },
    "pt": {
        "lack of transmitters": "Tx",
        "lack of receivers": "Rx",
        "fragmentation": "Frag",
        "QoTN": "OSNRN",
        "QoTO": "OSNRO",
        "crosstalk": "XTN",
        "crosstalk in other": "XTO",
        "other": "Outros",
    },
}

ALGO_STYLES = {
    "APAmem": {"color": "#1f77b4", "marker": "o", "linestyle": "solid"},
    "APAnoMem": {"color": "#2ca02c", "marker": "s", "linestyle": "dash"},
    "APAmin": {"color": "#4c78a8", "marker": "^", "linestyle": "dashdot"},
    "CPA": {"color": "#d62728", "marker": "D", "linestyle": "dot"},
    "CPSD": {"color": "#9467bd", "marker": "v", "linestyle": "long_dash"},
    "EnPA": {"color": "#8c564b", "marker": "P", "linestyle": "dense_dashdot"},
    "EPA": {"color": "#e377c2", "marker": "X", "linestyle": "dense_dotted"},
    "IMPA": {"color": "#7f7f7f", "marker": "*", "linestyle": "dash_dot_dot"},
    "PABS": {"color": "#17becf", "marker": "h", "linestyle": "loose_dashdot"},
}

MARKER_OPTIONS = [
    ("", "None"),
    ("o", "Circle (o)"),
    ("s", "Square (s)"),
    ("^", "Triangle up (^)"),
    ("v", "Triangle down (v)"),
    ("D", "Diamond (D)"),
    ("P", "Plus filled (P)"),
    ("X", "X filled (X)"),
    ("*", "Star (*)"),
    ("+", "Plus (+)"),
    ("x", "X (x)"),
    ("h", "Hexagon (h)"),
]

LINESTYLE_OPTIONS = [
    ("solid", "Solid (-)", "-", None),
    ("dash", "Dashed (--)", "--", (8, 4)),
    ("dashdot", "Dash-dot (-.)", "-.", (8, 3, 2, 3)),
    ("dot", "Dotted (:) ", ":", (2, 3)),
    ("long_dash", "Long dash", (0, (12, 4)), (12, 4)),
    ("dense_dashdot", "Dense dash-dot", (0, (6, 2, 2, 2)), (6, 2, 2, 2)),
    ("dense_dotted", "Dense dotted", (0, (1, 2)), (1, 2)),
    ("dash_dot_dot", "Dash-dot-dot", (0, (8, 3, 2, 3, 2, 3)), (8, 3, 2, 3, 2, 3)),
    ("loose_dashdot", "Loose dash-dot", (0, (10, 4, 2, 4)), (10, 4, 2, 4)),
    ("extra_long_dash", "Extra long dash", (0, (16, 5)), (16, 5)),
    ("short_long", "Short-short long", (0, (3, 2, 3, 2, 10, 3)), (3, 2, 3, 2, 10, 3)),
    ("none", "None", "", None),
]

MARKER_LABEL_TO_VALUE = {label: value for value, label in MARKER_OPTIONS}
MARKER_VALUE_TO_LABEL = {value: label for value, label in MARKER_OPTIONS}
LINESTYLE_ID_TO_LABEL = {style_id: label for style_id, label, _mpl, _dash in LINESTYLE_OPTIONS}
LINESTYLE_LABEL_TO_ID = {label: style_id for style_id, label, _mpl, _dash in LINESTYLE_OPTIONS}
LINESTYLE_ID_TO_SPEC = {style_id: mpl_spec for style_id, _label, mpl_spec, _dash in LINESTYLE_OPTIONS}
LINESTYLE_ID_TO_CANVAS_DASH = {style_id: dash_spec for style_id, _label, _mpl, dash_spec in LINESTYLE_OPTIONS}
DEFAULT_MARKER_CYCLE = ["o", "s", "^", "D", "v", "P", "X", "*", "h", "+", "x"]
DEFAULT_LINESTYLE_CYCLE = [
    "solid",
    "dash",
    "dashdot",
    "dot",
    "long_dash",
    "dense_dashdot",
    "dense_dotted",
    "dash_dot_dot",
    "loose_dashdot",
    "extra_long_dash",
    "short_long",
]
DEFAULT_COLOR_CYCLE = [
    "#1f77b4",  # blue
    "#2ca02c",  # green
    "#d62728",  # red
    "#9467bd",  # purple
    "#8c564b",  # brown
    "#e377c2",  # pink
    "#17becf",  # cyan
    "#ff7f0e",  # orange
    "#7f7f7f",  # gray
    "#bcbd22",  # olive
    "#4c78a8",  # alternate blue
]
Y_GRID_OPTIONS = [
    "Many lines (major + minor)",
    "Major lines only",
]

LEGEND_POSITION_OPTIONS = [
    "Inside (best)",
    "Inside (upper right)",
    "Inside (upper left)",
    "Inside (lower right)",
    "Inside (lower left)",
    "Inside (center right)",
    "Inside (center left)",
    "Inside (upper center)",
    "Inside (lower center)",
    "Inside (center)",
    "Bottom (outside)",
    "Top (outside)",
    "Right (outside)",
    "Left (outside)",
    "No legend",
]

KNOWN_METRIC_FILE_SUFFIXES = [
    "BitRateBlockingProbability",
    "BlockingProbability",
    "CrosstalkStatistics",
    "ModulationUtilization",
    "SpectrumUtilization",
]

LANGUAGE_DISPLAY_TO_CODE = {
    "English": "en",
    "Português": "pt",
}

LANGUAGE_CODE_TO_DISPLAY = {
    "en": "English",
    "pt": "Português",
}

COMPONENT_COLORS = {
    "lack of transmitters": "#7f7f7f",
    "lack of receivers": "#8c564b",
    "fragmentation": "#d62728",
    "QoTN": "#4c72b0",
    "QoTO": "#ed7d31",
    "crosstalk": "#00b050",
    "crosstalk in other": "#ffc000",
    "other": "#e377c2",
}


COMPONENT_PLOT_ORDER = [
    "QoTN",                # OSNRN
    "QoTO",                # OSNRO
    "crosstalk",           # XTN
    "crosstalk in other",  # XTO
    "lack of transmitters",
    "lack of receivers",
    "fragmentation",
    "other",
]

GRAPH_TEXT_DEFAULTS = {
    "en": {
        "line_x": "Network load (Erlangs)",
        "line_y": "{metric}",
        "line_y_log": "{metric} (log10)",
        "line_title": "",
        "stacked_bar_x": "Algorithms",
        "stacked_bar_y": "Blocking probability",
        "stacked_bar_title": "Stacked Components Breakdown at {load} Erlangs",
        "percent_bar_x": "Power assignment algorithms",
        "percent_bar_y": "% of blocking components",
        "percent_bar_title": "Normalized blocking components at {load} Erlangs",
        "simple_bar_x": "Algorithms",
        "simple_bar_y": "{metric}",
        "simple_bar_title": "{metric} at {load} Erlangs",
    },
    "pt": {
        "line_x": "Carga da rede (Erlangs)",
        "line_y": "{metric}",
        "line_y_log": "{metric} (log10)",
        "line_title": "",
        "stacked_bar_x": "Algoritmos",
        "stacked_bar_y": "Probabilidade de bloqueio",
        "stacked_bar_title": "Decomposição empilhada dos componentes em {load} Erlangs",
        "percent_bar_x": "Algoritmos de atribuição de potência",
        "percent_bar_y": "% das componentes de bloqueio",
        "percent_bar_title": "Componentes de bloqueio normalizados em {load} Erlangs",
        "simple_bar_x": "Algoritmos",
        "simple_bar_y": "{metric}",
        "simple_bar_title": "{metric} em {load} Erlangs",
    },
}

GRAPH_TEXT_KEYS = [
    ("line_x", "Line plot - X axis"),
    ("line_y", "Line plot - Y axis"),
    ("line_y_log", "Line plot - Y axis (log scale)"),
    ("line_title", "Line plot - Title"),
    ("stacked_bar_x", "Stacked bar plot - X axis"),
    ("stacked_bar_y", "Stacked bar plot - Y axis"),
    ("stacked_bar_title", "Stacked bar plot - Title"),
    ("percent_bar_x", "100% stacked bar plot - X axis"),
    ("percent_bar_y", "100% stacked bar plot - Y axis"),
    ("percent_bar_title", "100% stacked bar plot - Title"),
    ("simple_bar_x", "Simple bar plot - X axis"),
    ("simple_bar_y", "Simple bar plot - Y axis"),
    ("simple_bar_title", "Simple bar plot - Title"),
]

EXCLUDED_COMPONENT_METRICS = {"numm attempts qoto counter (pabs)"}

METRIC_LABELS = {
    "Blocking probability": {"en": "Blocking probability", "pt": "Probabilidade de bloqueio"},
    "BlockingProbability": {"en": "Blocking probability", "pt": "Probabilidade de bloqueio"},
    "Bit rate blocking probability": {
        "en": "Bit rate blocking probability",
        "pt": "Probabilidade de bloqueio por taxa de bits",
    },
    "BitRateBlockingProbability": {
        "en": "Bit rate blocking probability",
        "pt": "Probabilidade de bloqueio por taxa de bits",
    },
    "BitRate blocking probability": {
        "en": "Bit rate blocking probability",
        "pt": "Probabilidade de bloqueio por taxa de bits",
    },
}

LOG_ERROR_MODE_OPTIONS = [
    "Hide lower part when CI lower <= 0",
    "Compute interval in log scale",
    "Mark truncated lower error",
]


THEME_PRESETS = {
    "Gray Slate": {
        "base_theme": "clam",
        "root_bg": "#eceff3",
        "bg": "#eceff3",
        "card": "#f7f8fa",
        "border": "#c8cdd4",
        "muted": "#667085",
        "tab_bg": "#dde2e8",
        "tab_fg": "#3c4654",
        "title_fg": "#2c3440",
        "status_fg": "#556070",
        "entry_bg": "#ffffff",
        "entry_border": "#bcc4cd",
        "primary_bg": "#e5e9ef",
        "primary_fg": "#344054",
        "primary_active": "#dbe1e8",
        "primary_pressed": "#d0d7df",
        "accent_bg": "#f1f1f1",
        "accent_fg": "#111111",
        "accent_active": "#e7e7e7",
        "accent_pressed": "#dddddd",
        "compute_bg": "#f1f1f1",
        "compute_fg": "#111111",
        "compute_active": "#e7e7e7",
        "compute_pressed": "#dddddd",
        "tree_heading_bg": "#e8ecf1",
        "tree_heading_fg": "#344054",
        "tree_bg": "#ffffff",
        "button_border": "#a7a7a7",
    },
    "Windows 7 Blue": {
        "base_theme": "vista",
        "root_bg": "#dfeaf7",
        "bg": "#dfeaf7",
        "card": "#f8fbff",
        "border": "#9fb8d8",
        "muted": "#5d6f87",
        "tab_bg": "#d7e4f5",
        "tab_fg": "#1f4d7f",
        "title_fg": "#1d426b",
        "status_fg": "#4b6481",
        "entry_bg": "#ffffff",
        "entry_border": "#9fb8d8",
        "primary_bg": "#edf4fd",
        "primary_fg": "#1f3b5b",
        "primary_active": "#e2edf9",
        "primary_pressed": "#d6e5f7",
        "accent_bg": "#c9dcf4",
        "accent_fg": "#111111",
        "accent_active": "#b9d0ee",
        "accent_pressed": "#abc4e6",
        "compute_bg": "#d8e5f5",
        "compute_fg": "#111111",
        "compute_active": "#cadcf0",
        "compute_pressed": "#bdd2ea",
        "tree_heading_bg": "#e8f1fb",
        "tree_heading_fg": "#1f4d7f",
        "tree_bg": "#ffffff",
        "button_border": "#7f9db9",
    },

    "Traditional": {
        "base_theme": "classic",
        "root_bg": "#d9d9d9",
        "bg": "#d9d9d9",
        "card": "#efefef",
        "border": "#a6a6a6",
        "muted": "#4f4f4f",
        "tab_bg": "#d9d9d9",
        "tab_fg": "#202020",
        "title_fg": "#202020",
        "status_fg": "#3f3f3f",
        "entry_bg": "#ffffff",
        "entry_border": "#8f8f8f",
        "primary_bg": "#e6e6e6",
        "primary_fg": "#202020",
        "primary_active": "#dcdcdc",
        "primary_pressed": "#d2d2d2",
        "accent_bg": "#c6d6ea",
        "accent_fg": "#202020",
        "accent_active": "#b8cade",
        "accent_pressed": "#aabed4",
        "compute_bg": "#d8d0c0",
        "compute_fg": "#202020",
        "compute_active": "#c9c1b1",
        "compute_pressed": "#bbb3a3",
        "tree_heading_bg": "#e5e5e5",
        "tree_heading_fg": "#202020",
        "tree_bg": "#ffffff",
    },
    "Sage Light": {
        "base_theme": "clam",
        "root_bg": "#f4f6f2",
        "bg": "#f4f6f2",
        "card": "#ffffff",
        "border": "#d8dfd2",
        "muted": "#6b7280",
        "tab_bg": "#e7ede2",
        "tab_fg": "#31473a",
        "title_fg": "#1f2937",
        "status_fg": "#475569",
        "entry_bg": "#ffffff",
        "entry_border": "#cfd8c8",
        "primary_bg": "#e8f0e7",
        "primary_fg": "#2f4f3e",
        "primary_active": "#dbe7d9",
        "primary_pressed": "#cfdecc",
        "accent_bg": "#3f7d58",
        "accent_fg": "#ffffff",
        "accent_active": "#35694b",
        "accent_pressed": "#2d5a40",
        "compute_bg": "#7c5c3b",
        "compute_fg": "#ffffff",
        "compute_active": "#694c31",
        "compute_pressed": "#583f29",
        "tree_heading_bg": "#eef3ea",
        "tree_heading_fg": "#294038",
        "tree_bg": "#ffffff",
    },
    "Ocean Mist": {
        "base_theme": "clam",
        "root_bg": "#eff5f8",
        "bg": "#eff5f8",
        "card": "#ffffff",
        "border": "#d6e1e8",
        "muted": "#64748b",
        "tab_bg": "#e3edf3",
        "tab_fg": "#24445c",
        "title_fg": "#16324a",
        "status_fg": "#466079",
        "entry_bg": "#ffffff",
        "entry_border": "#c9d9e4",
        "primary_bg": "#e6f0f6",
        "primary_fg": "#1f4d6b",
        "primary_active": "#d9e8f1",
        "primary_pressed": "#cde0eb",
        "accent_bg": "#2a6f97",
        "accent_fg": "#ffffff",
        "accent_active": "#245f81",
        "accent_pressed": "#1d506d",
        "compute_bg": "#4f7cac",
        "compute_fg": "#ffffff",
        "compute_active": "#446a92",
        "compute_pressed": "#395a7b",
        "tree_heading_bg": "#edf4f8",
        "tree_heading_fg": "#1f4d6b",
        "tree_bg": "#ffffff",
    },
    "Rose Sand": {
        "base_theme": "clam",
        "root_bg": "#faf3f0",
        "bg": "#faf3f0",
        "card": "#ffffff",
        "border": "#ead8d0",
        "muted": "#7b6d67",
        "tab_bg": "#f2e6e1",
        "tab_fg": "#6a3d39",
        "title_fg": "#4a2c2a",
        "status_fg": "#6f5b56",
        "entry_bg": "#ffffff",
        "entry_border": "#e5d1ca",
        "primary_bg": "#f6e8e3",
        "primary_fg": "#7a4b45",
        "primary_active": "#f0ddd6",
        "primary_pressed": "#e9d1c9",
        "accent_bg": "#b56576",
        "accent_fg": "#ffffff",
        "accent_active": "#9f5968",
        "accent_pressed": "#8a4d59",
        "compute_bg": "#6d597a",
        "compute_fg": "#ffffff",
        "compute_active": "#5d4b68",
        "compute_pressed": "#4f4059",
        "tree_heading_bg": "#f8efeb",
        "tree_heading_fg": "#7a4b45",
        "tree_bg": "#ffffff",
    },
}


GAIN_METRIC_CANONICAL = {
    "blockingprobability": "BlockingProbability",
    "bitrateblockingprobability": "BitRateBlockingProbability",
}


# ======================================================
# Utility functions
# ======================================================
def get_alpha_from_conf(conf_str: str) -> float:
    return {"90%": 0.10, "95%": 0.05, "99%": 0.01}.get(conf_str, 0.05)


def bootstrap_ci_mean(row_1d, alpha=0.05, n_boot=2000, rng=None):
    x = np.asarray(row_1d, dtype=float)
    x = x[~np.isnan(x)]
    if x.size < 2:
        m = float(np.nanmean(x)) if x.size else 0.0
        return m, m

    if rng is None:
        rng = np.random.default_rng()

    n = x.size
    samples = rng.choice(x, size=(n_boot, n), replace=True)
    boot_means = samples.mean(axis=1)
    lower = np.percentile(boot_means, 100 * (alpha / 2))
    upper = np.percentile(boot_means, 100 * (1 - alpha / 2))
    return float(lower), float(upper)


def detect_rep_columns(df: pd.DataFrame):
    rep_cols = [c for c in df.columns if str(c).lower().startswith("rep")]

    def rep_key(col):
        s = str(col).lower()
        suf = s[3:]
        try:
            return (0, int(suf))
        except Exception:
            return (1, s)

    rep_cols.sort(key=rep_key)
    return rep_cols


def choose_rep_columns(df: pd.DataFrame, n_rep_user: int, algo_name_for_msg: str):
    rep_cols_all = detect_rep_columns(df)
    if not rep_cols_all:
        return [], 0

    n_avail = len(rep_cols_all)
    if n_rep_user <= 0:
        return rep_cols_all, n_avail

    if n_rep_user > n_avail:
        messagebox.showwarning(
            "Warning",
            f"{algo_name_for_msg}: CSV has only {n_avail} replications, but the GUI requested {n_rep_user}. Using {n_avail}.",
        )
        return rep_cols_all, n_avail

    if n_rep_user < n_avail:
        messagebox.showwarning(
            "Warning",
            f"{algo_name_for_msg}: CSV has {n_avail} replications, but the GUI requested {n_rep_user}. Using the first {n_rep_user}.",
        )
        return rep_cols_all[:n_rep_user], n_rep_user

    return rep_cols_all, n_avail


def parse_rep_values(df: pd.DataFrame, rep_cols):
    for c in rep_cols:
        df[c] = pd.to_numeric(df[c].astype(str).str.replace(",", ".", regex=False), errors="coerce")
    return df


def match_component_keyword(metric: str):
    metric_l = str(metric).lower()
    for kw in sorted(COMPONENT_KEYWORDS, key=len, reverse=True):
        if kw.lower() in metric_l:
            return kw
    return None


def is_component_selectable(metric: str) -> bool:
    if match_component_keyword(metric) is None:
        return False
    return metric.strip().lower() not in EXCLUDED_COMPONENT_METRICS


def get_component_plot_order(components):
    priority = {name: idx for idx, name in enumerate(COMPONENT_PLOT_ORDER)}

    def sort_key(comp):
        keyword = match_component_keyword(comp)
        if keyword in priority:
            return (0, priority[keyword], str(comp).lower())
        return (1, 999, str(comp).lower())

    return sorted(components, key=sort_key)


def canonical_metric_key(metric: str) -> str | None:
    norm = "".join(ch for ch in str(metric).lower() if ch.isalnum())
    return GAIN_METRIC_CANONICAL.get(norm)


def is_gain_metric(metric: str) -> bool:
    return canonical_metric_key(metric) is not None


def format_float(value: float | int | str) -> str:
    if value is None:
        return ""
    try:
        v = float(value)
    except Exception:
        return str(value)
    if np.isnan(v):
        return "nan"
    return f"{v:.6e}"


class ScrollableWindow(ttk.Frame):
    def __init__(self, master):
        super().__init__(master)
        self.pack(fill="both", expand=True)
        canvas = tk.Canvas(self, highlightthickness=0)
        scrollbar = ttk.Scrollbar(self, orient="vertical", command=canvas.yview)
        self.inner = ttk.Frame(canvas)
        self.inner.bind(
            "<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all"))
        )
        window_id = canvas.create_window((0, 0), window=self.inner, anchor="nw")
        canvas.configure(yscrollcommand=scrollbar.set)

        def resize_inner(event):
            canvas.itemconfigure(window_id, width=event.width)

        canvas.bind("<Configure>", resize_inner)
        canvas.pack(side="left", fill="both", expand=True)
        scrollbar.pack(side="right", fill="y")


# ======================================================
# Main GUI
# ======================================================
class GenericAnalyzerGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("SimGraph - Simulation Graph Analyzer")
        self.root.geometry("1220x820")
        self.root.minsize(1080, 720)

        self.csv_files: list[str] = []
        self.generated_temp_dir: str | None = None
        self.all_metrics: list[str] = []
        self.components_available: list[str] = []
        self.component_vars: dict[str, tk.BooleanVar] = {}
        self.algorithm_aliases: dict[str, str] = {}

        self.custom_graph_texts = deepcopy(GRAPH_TEXT_DEFAULTS)
        self.custom_component_labels = deepcopy(COMPONENT_LABEL_DEFAULTS)
        self.custom_percent_component_labels = deepcopy(PERCENT_COMPONENT_LABEL_DEFAULTS)
        self.custom_line_styles = {key: dict(value) for key, value in ALGO_STYLES.items()}

        self.last_fig = None
        self.last_ci_rows: list[dict] = []
        self.last_best_rows: list[dict] = []
        self.last_ci_metric: str = ""
        self.last_ci_algorithm: str = ""
        self.pending_metric_from_customizations: str = ""
        self.pending_component_selection_from_customizations: dict[str, bool] = {}
        self.status_var = tk.StringVar(value="Load CSV files to begin.")

        self.plot_type = tk.StringVar(value="line")
        self.log_scale = tk.BooleanVar(value=False)
        self.graph_language = tk.StringVar(value="en")
        self.graph_language_display = tk.StringVar(value=LANGUAGE_CODE_TO_DISPLAY.get("en", "English"))
        self.bar_plot_mode = tk.StringVar(value="absolute")
        self.log_error_mode = tk.StringVar(value="Mark truncated lower error")
        self.y_grid_mode = tk.StringVar(value="Many lines (major + minor)")
        self.theme_name = tk.StringVar(value="Gray Slate")
        self.metric_var = tk.StringVar(value="")
        self.conf_level = tk.StringVar(value="95%")
        self.ci_method = tk.StringVar(value="t-Student")
        self.legend_position = tk.StringVar(value="Inside (best)")
        self.axis_text_bold = tk.BooleanVar(value=False)
        self.tick_text_bold = tk.BooleanVar(value=False)

        self._build_styles()
        self._build_menu()
        self._build_ui()
        self.theme_name.trace_add("write", self._on_theme_changed)
        self.graph_language_display.trace_add("write", self._sync_graph_language_from_display)
        self.graph_language.trace_add("write", self._sync_graph_language_display)

    # ------------------------------
    # UI helpers
    # ------------------------------
    def _sync_graph_language_from_display(self, *args):
        display_value = self.graph_language_display.get()
        code_value = LANGUAGE_DISPLAY_TO_CODE.get(display_value, "en")
        if self.graph_language.get() != code_value:
            self.graph_language.set(code_value)

    def _sync_graph_language_display(self, *args):
        code_value = self.graph_language.get()
        display_value = LANGUAGE_CODE_TO_DISPLAY.get(code_value, "English")
        if self.graph_language_display.get() != display_value:
            self.graph_language_display.set(display_value)

    def _build_styles(self):
        self.root.option_add("*Font", "{Segoe UI} 10")
        style = ttk.Style(self.root)
        self.available_ttk_themes = tuple(style.theme_names())
        self.original_ttk_theme = style.theme_use()
        self.style = style
        self.apply_theme(self.theme_name.get())

    def _pick_available_theme(self, candidates, fallback=None):
        for name in candidates:
            if name in self.available_ttk_themes:
                return name
        return fallback or self.original_ttk_theme or "clam"

    def _resolve_base_theme(self, theme_name: str):
        palette = THEME_PRESETS.get(theme_name, THEME_PRESETS["Gray Slate"])
        base = palette.get("base_theme", "clam")
        if base == "vista":
            return self._pick_available_theme(["vista", "xpnative", "winnative", "default", "clam"], fallback="clam")
        if base == "classic":
            return self._pick_available_theme(["classic", "default", "clam"], fallback="clam")
        return self._pick_available_theme([base, "clam"], fallback="clam")

    def apply_theme(self, theme_name=None):
        theme_name = theme_name or self.theme_name.get()
        palette = THEME_PRESETS.get(theme_name, THEME_PRESETS["Gray Slate"])
        style = self.style

        base_theme = self._resolve_base_theme(theme_name)
        try:
            style.theme_use(base_theme)
        except Exception:
            pass

        self.root.configure(bg=palette["root_bg"])

        style.configure("TFrame", background=palette["bg"])
        style.configure("TLabel", background=palette["bg"])
        style.configure("TNotebook", background=palette["bg"], borderwidth=0)
        style.configure("TNotebook.Tab", padding=(12, 8), background=palette["tab_bg"], foreground=palette["tab_fg"])
        style.map("TNotebook.Tab", background=[("selected", palette["card"])], foreground=[("selected", palette["title_fg"])])

        style.configure("Title.TLabel", font=("Segoe UI", 16, "bold"), foreground=palette["title_fg"], background=palette["bg"])
        style.configure("Subtitle.TLabel", foreground=palette["muted"], background=palette["bg"])
        style.configure("Status.TLabel", foreground=palette["status_fg"], background=palette["bg"])

        style.configure("Card.TLabelframe", background=palette["card"], bordercolor=palette["border"], relief="solid", borderwidth=1, padding=12)
        style.configure("Card.TLabelframe.Label", font=("Segoe UI", 10, "bold"), foreground=palette["tab_fg"], background=palette["bg"])
        style.configure("TLabelframe", background=palette["card"])
        style.configure("TLabelframe.Label", background=palette["bg"])

        style.configure(
            "TEntry",
            fieldbackground=palette["entry_bg"],
            bordercolor=palette["entry_border"],
            lightcolor=palette["entry_border"],
            darkcolor=palette["entry_border"],
            padding=4,
        )
        style.configure("TCombobox", fieldbackground=palette["entry_bg"], padding=3)

        style.configure("Primary.TButton", padding=(11, 7), background=palette["primary_bg"], foreground=palette["primary_fg"], bordercolor=palette["border"])
        style.map(
            "Primary.TButton",
            background=[("active", palette["primary_active"]), ("pressed", palette["primary_pressed"])],
            foreground=[("!disabled", palette["primary_fg"]), ("disabled", "#a0a0a0")],
        )

        style.configure("Accent.TButton", padding=(12, 8), background=palette["accent_bg"], foreground=palette["accent_fg"], bordercolor=palette["accent_bg"])
        style.map(
            "Accent.TButton",
            background=[("active", palette["accent_active"]), ("pressed", palette["accent_pressed"])],
            foreground=[("!disabled", palette["accent_fg"]), ("active", palette["accent_fg"]), ("pressed", palette["accent_fg"]), ("disabled", "#f0f0f0")],
        )

        style.configure("Compute.TButton", padding=(12, 8), background=palette["compute_bg"], foreground=palette["compute_fg"], bordercolor=palette.get("button_border", palette["compute_bg"]))
        style.map(
            "Compute.TButton",
            background=[("active", palette["compute_active"]), ("pressed", palette["compute_pressed"])],
            foreground=[("!disabled", palette["compute_fg"]), ("active", palette["compute_fg"]), ("pressed", palette["compute_fg"]), ("disabled", "#f0f0f0")],
        )

        if hasattr(self, "generate_button"):
            self._apply_action_button_theme(self.generate_button, kind="accent", palette=palette)
        if hasattr(self, "compute_button"):
            self._apply_action_button_theme(self.compute_button, kind="compute", palette=palette)

        style.configure("Treeview", background=palette.get("tree_bg", "#ffffff"), fieldbackground=palette.get("tree_bg", "#ffffff"), rowheight=26, bordercolor=palette["border"])
        style.configure("Treeview.Heading", font=("Segoe UI", 9, "bold"), background=palette["tree_heading_bg"], foreground=palette["tree_heading_fg"])

        if hasattr(self, "metrics_preview"):
            bg = palette.get("tree_bg", "#ffffff")
            try:
                self.metrics_preview.configure(bg=bg)
                self.files_text.configure(bg=bg)
            except Exception:
                pass

        if hasattr(self, "status_var"):
            suffix = f" (base ttk theme: {base_theme})" if theme_name in {"Windows 7 Blue", "Traditional"} else ""
            self.set_status(f"Theme applied: {theme_name}{suffix}")

    def _on_theme_changed(self, *args):
        self.apply_theme(self.theme_name.get())

    def _apply_action_button_theme(self, button, kind, palette):
        bg_key = "accent_bg" if kind == "accent" else "compute_bg"
        fg_key = "accent_fg" if kind == "accent" else "compute_fg"
        active_key = "accent_active" if kind == "accent" else "compute_active"
        pressed_key = "accent_pressed" if kind == "accent" else "compute_pressed"
        relief = "raised"
        bd = 1
        if self.theme_name.get() in {"Gray Slate", "Traditional", "Windows 7 Blue"}:
            relief = "raised"
            bd = 1
        button.configure(
            bg=palette[bg_key],
            fg=palette[fg_key],
            activebackground=palette[active_key],
            activeforeground=palette[fg_key],
            disabledforeground="#7a7a7a",
            relief=relief,
            bd=bd,
            highlightthickness=1,
            highlightbackground=palette.get("button_border", palette["border"]),
            highlightcolor=palette.get("button_border", palette["border"]),
        )
        button.bind("<ButtonPress-1>", lambda e, b=button, c=palette[pressed_key]: b.configure(bg=c), add="+")
        button.bind("<ButtonRelease-1>", lambda e, b=button, c=palette[bg_key]: b.configure(bg=c), add="+")

    def _build_menu(self):
        menubar = tk.Menu(self.root)

        file_menu = tk.Menu(menubar, tearoff=0)
        file_menu.add_command(label="Load CSV files", command=self.load_files)
        file_menu.add_separator()
        file_menu.add_command(label="Save customizations...", command=self.save_customizations)
        file_menu.add_command(label="Load customizations...", command=self.load_customizations)
        file_menu.add_separator()
        file_menu.add_command(label="Exit", command=self.root.quit)
        menubar.add_cascade(label="File", menu=file_menu)

        theme_menu = tk.Menu(menubar, tearoff=0)
        for theme_name in THEME_PRESETS.keys():
            theme_menu.add_radiobutton(label=theme_name, variable=self.theme_name, value=theme_name)
        menubar.add_cascade(label="Theme", menu=theme_menu)

        export_menu = tk.Menu(menubar, tearoff=0)
        export_menu.add_command(label="Save last plot (SVG)", command=lambda: self.save_last_plot("svg"))
        export_menu.add_command(label="Save last plot (PDF)", command=lambda: self.save_last_plot("pdf"))
        export_menu.add_command(label="Export last CI table (CSV)", command=self.export_last_ci_table)
        menubar.add_cascade(label="Export", menu=export_menu)

        self.root.config(menu=menubar)
        self.menubar = menubar

    def _build_ui(self):
        outer = ttk.Frame(self.root, padding=12)
        outer.pack(fill="both", expand=True)
        outer.columnconfigure(0, weight=1)
        outer.rowconfigure(2, weight=1)

        header = ttk.Frame(outer)
        header.grid(row=0, column=0, sticky="ew")
        header.columnconfigure(0, weight=1)
        ttk.Label(header, text="Simulation Graph Analyzer", style="Title.TLabel").grid(
            row=0, column=0, sticky="w"
        )
        ttk.Label(
            header,
            text="Generate charts, customize labels, inspect confidence intervals, and compare algorithm gains.",
            style="Subtitle.TLabel",
        ).grid(row=1, column=0, sticky="w", pady=(2, 0))

        toolbar = ttk.LabelFrame(outer, text="Actions", style="Card.TLabelframe")
        toolbar.grid(row=1, column=0, sticky="ew", pady=(10, 10))
        for col in range(5):
            toolbar.columnconfigure(col, weight=1)

        ttk.Button(toolbar, text="📂 Load CSV files", command=self.load_files, style="Primary.TButton").grid(
            row=0, column=0, padx=4, pady=4, sticky="ew"
        )
        self.algo_names_button = ttk.Button(
            toolbar,
            text="✏ Configure algorithm names",
            command=self.open_algorithm_names_window,
            state="disabled",
            style="Primary.TButton",
        )
        self.algo_names_button.grid(row=0, column=1, padx=4, pady=4, sticky="ew")
        ttk.Button(toolbar, text="📝 Edit graph texts", command=self.open_graph_texts_window, style="Primary.TButton").grid(
            row=0, column=2, padx=4, pady=4, sticky="ew"
        )
        ttk.Button(toolbar, text="🏷 Edit component legends", command=self.open_component_legends_window, style="Primary.TButton").grid(
            row=0, column=3, padx=4, pady=4, sticky="ew"
        )
        self.line_styles_button = ttk.Button(
            toolbar,
            text="🎨 Edit line styles",
            command=self.open_line_styles_window,
            state="disabled",
            style="Primary.TButton",
        )
        self.line_styles_button.grid(row=0, column=4, padx=4, pady=4, sticky="ew")
        self.compute_button = tk.Button(toolbar, text="Compute gains", command=self.open_gain_window, cursor="hand2")

        self.components_button = ttk.Button(
            toolbar,
            text="☑ Select components for bar plot (0)",
            command=self.open_components_window,
            state="disabled",
            style="Primary.TButton",
        )
        self.components_button.grid(row=1, column=0, padx=4, pady=4, sticky="ew")
        self.save_plot_button = ttk.Button(
            toolbar,
            text="🖼 Save last plot (SVG)",
            command=lambda: self.save_last_plot("svg"),
            state="disabled",
            style="Primary.TButton",
        )
        self.save_plot_button.grid(row=1, column=1, padx=4, pady=4, sticky="ew")
        self.export_ci_button = ttk.Button(
            toolbar,
            text="📋 Export last CI table (CSV)",
            command=self.export_last_ci_table,
            state="disabled",
            style="Primary.TButton",
        )
        self.export_ci_button.grid(row=1, column=2, padx=4, pady=4, sticky="ew")
        self.generate_button = tk.Button(toolbar, text="Generate plot", command=self.generate_plot, cursor="hand2")
        self.generate_button.grid(row=1, column=3, padx=4, pady=4, sticky="ew")
        self.compute_button.grid(row=1, column=4, padx=4, pady=4, sticky="ew")

        content = ttk.Panedwindow(outer, orient="horizontal")
        content.grid(row=2, column=0, sticky="nsew")

        left = ttk.Frame(content)
        right = ttk.Frame(content)
        content.add(left, weight=2)
        content.add(right, weight=3)

        left.columnconfigure(0, weight=1)
        left.rowconfigure(1, weight=1)
        right.columnconfigure(0, weight=1)
        right.rowconfigure(0, weight=1)

        metrics_frame = ttk.LabelFrame(left, text="Metric selection", style="Card.TLabelframe")
        metrics_frame.grid(row=0, column=0, sticky="nsew")
        metrics_frame.columnconfigure(0, weight=1)
        metrics_frame.rowconfigure(3, weight=1)
        ttk.Label(metrics_frame, text="Select the metric to analyze and plot.").grid(row=0, column=0, sticky="w", pady=(0, 6))
        self.metric_combo = ttk.Combobox(metrics_frame, textvariable=self.metric_var, values=[], state="readonly")
        self.metric_combo.grid(row=1, column=0, sticky="ew", pady=(0, 8))
        ttk.Label(metrics_frame, text="Loaded metrics:").grid(row=2, column=0, sticky="w", pady=(4, 4))
        metrics_text_frame = ttk.Frame(metrics_frame)
        metrics_text_frame.grid(row=3, column=0, sticky="nsew")
        metrics_text_frame.columnconfigure(0, weight=1)
        metrics_text_frame.rowconfigure(0, weight=1)
        self.metrics_preview = tk.Text(metrics_text_frame, height=14, wrap="word")
        self.metrics_preview.grid(row=0, column=0, sticky="nsew")
        self.metrics_preview.configure(state="disabled")
        metrics_scroll = ttk.Scrollbar(metrics_text_frame, orient="vertical", command=self.metrics_preview.yview)
        metrics_scroll.grid(row=0, column=1, sticky="ns")
        self.metrics_preview.configure(yscrollcommand=metrics_scroll.set)

        info_frame = ttk.LabelFrame(left, text="Loaded files", style="Card.TLabelframe")
        info_frame.grid(row=1, column=0, sticky="nsew", pady=(10, 0))
        info_frame.columnconfigure(0, weight=1)
        info_frame.rowconfigure(0, weight=1)
        self.files_text = tk.Text(info_frame, height=10, wrap="word")
        self.files_text.grid(row=0, column=0, sticky="nsew")
        self.files_text.configure(state="disabled")
        files_scroll = ttk.Scrollbar(info_frame, orient="vertical", command=self.files_text.yview)
        files_scroll.grid(row=0, column=1, sticky="ns")
        self.files_text.configure(yscrollcommand=files_scroll.set)

        notebook = ttk.Notebook(right)
        notebook.grid(row=0, column=0, sticky="nsew")

        self._build_plot_tab(notebook)
        self._build_stats_tab(notebook)
        self._build_appearance_tab(notebook)
        self._build_margins_tab(notebook)

        status_frame = ttk.Frame(outer)
        status_frame.grid(row=3, column=0, sticky="ew", pady=(10, 0))
        ttk.Separator(status_frame, orient="horizontal").pack(fill="x", pady=(0, 6))
        ttk.Label(status_frame, textvariable=self.status_var, style="Status.TLabel").pack(anchor="w")

        self.plot_type.trace_add("write", self.update_plot_mode)
        self.update_plot_mode()

    def _build_plot_tab(self, notebook):
        tab = ttk.Frame(notebook, padding=12)
        notebook.add(tab, text="Plot setup")
        tab.columnconfigure(0, weight=1)
        tab.columnconfigure(1, weight=1)

        frame_type = ttk.LabelFrame(tab, text="Plot type", style="Card.TLabelframe")
        frame_type.grid(row=0, column=0, sticky="nsew", padx=(0, 6), pady=(0, 8))
        ttk.Radiobutton(frame_type, text="Line plot (any metric)", variable=self.plot_type, value="line").pack(anchor="w")
        ttk.Radiobutton(frame_type, text="Bar plot (components only)", variable=self.plot_type, value="bar").pack(anchor="w", pady=(4, 0))
        ttk.Checkbutton(frame_type, text="Use log scale (log10) for line plot", variable=self.log_scale).pack(anchor="w", pady=(10, 0))

        frame_lang = ttk.LabelFrame(tab, text="Language and bar mode", style="Card.TLabelframe")
        frame_lang.grid(row=0, column=1, sticky="nsew", padx=(6, 0), pady=(0, 8))
        self._add_combo_row(frame_lang, 0, "Graph language", self.graph_language_display, ["English", "Português"])
        self._add_combo_row(frame_lang, 1, "Bar plot mode", self.bar_plot_mode, ["absolute", "percent"])
        self._add_combo_row(frame_lang, 2, "Log-scale error bar handling", self.log_error_mode, LOG_ERROR_MODE_OPTIONS)
        self._add_combo_row(frame_lang, 3, "Y-axis grid style", self.y_grid_mode, Y_GRID_OPTIONS)

        frame_load = ttk.LabelFrame(tab, text="Loads and replications", style="Card.TLabelframe")
        frame_load.grid(row=1, column=0, sticky="nsew", padx=(0, 6), pady=(0, 8))
        self.init_load = self._add_entry_row(frame_load, 0, "Initial load", "500")
        self.load_step = self._add_entry_row(frame_load, 1, "Load increment", "250")
        self.n_rep = self._add_entry_row(frame_load, 2, "Replications (0=auto)", "0")
        self.bar_load_point = self._add_entry_row(frame_load, 3, "Bar plot load point", "500")
        self.load_filter = self._add_entry_row(frame_load, 4, "Specific loads filter", "")
        ttk.Label(frame_load, text="Example: 500, 1000, 1500").grid(row=5, column=0, columnspan=2, sticky="w", pady=(4, 0))

        frame_note = ttk.LabelFrame(tab, text="Notes", style="Card.TLabelframe")
        frame_note.grid(row=1, column=1, sticky="nsew", padx=(6, 0), pady=(0, 8))
        ttk.Label(
            frame_note,
            text=(
                "When a line plot is generated, the application prints a table in the console with:\n"
                "load, mean, CI lower, CI upper, and whether lower-error truncation occurred.\n"
                "CI = Confidence Interval."
            ),
            justify="left",
        ).pack(anchor="w")

    def _build_stats_tab(self, notebook):
        tab = ttk.Frame(notebook, padding=12)
        notebook.add(tab, text="Statistics")
        tab.columnconfigure(0, weight=1)
        tab.columnconfigure(1, weight=1)

        frame_ci = ttk.LabelFrame(tab, text="Confidence interval (CI)", style="Card.TLabelframe")
        frame_ci.grid(row=0, column=0, sticky="nsew", padx=(0, 6), pady=(0, 8))
        self._add_combo_row(frame_ci, 0, "Confidence level", self.conf_level, ["90%", "95%", "99%"])
        self._add_combo_row(frame_ci, 1, "CI method", self.ci_method, ["t-Student", "Bootstrap"])
        self.bootstrap_n = self._add_entry_row(frame_ci, 2, "Bootstrap resamples", "2000")

        frame_note = ttk.LabelFrame(tab, text="Notes", style="Card.TLabelframe")
        frame_note.grid(row=1, column=0, columnspan=2, sticky="nsew", pady=(0, 8))
        ttk.Label(
            frame_note,
            text=(
                "Confidence level: selects the confidence used to build the interval around the mean.\n"
                "CI method: chooses how the confidence interval is computed.\n"
                "Bootstrap resamples: number of resamples used when the Bootstrap method is selected.\n"
                "Gain formula: compares the selected algorithm against the reference algorithm using the chosen metric."
            ),
            justify="left",
        ).pack(anchor="w")

    def _build_appearance_tab(self, notebook):
        tab = ttk.Frame(notebook, padding=12)
        notebook.add(tab, text="Appearance")
        tab.columnconfigure(0, weight=1)

        frame = ttk.LabelFrame(tab, text="Font sizes, weight and legend", style="Card.TLabelframe")
        frame.grid(row=0, column=0, sticky="nsew", pady=(0, 8))
        self.axis_font = self._add_entry_row(frame, 0, "Axis label font", "12")
        self.tick_font = self._add_entry_row(frame, 1, "Tick font", "11")
        self.legend_font = self._add_entry_row(frame, 2, "Legend font", "11")
        ttk.Checkbutton(frame, text="Axis labels in bold", variable=self.axis_text_bold).grid(row=3, column=0, columnspan=2, sticky="w", pady=(2, 2))
        ttk.Checkbutton(frame, text="Axis ticks in bold", variable=self.tick_text_bold).grid(row=4, column=0, columnspan=2, sticky="w", pady=(2, 2))
        self._add_combo_row(frame, 5, "Legend position", self.legend_position, LEGEND_POSITION_OPTIONS)
        ttk.Label(frame, text="Use the Theme menu above to switch the application look.").grid(row=6, column=0, columnspan=2, sticky="w", pady=(6, 0))

        frame_note = ttk.LabelFrame(tab, text="Notes", style="Card.TLabelframe")
        frame_note.grid(row=1, column=0, sticky="nsew")
        ttk.Label(
            frame_note,
            text=(
                "Axis label font: controls the size of the X and Y axis labels.\n"
                "Tick font: controls the size of the tick labels on both axes.\n"
                "Axis labels in bold: makes the X and Y axis labels bold.\n"
                "Axis ticks in bold: makes the tick labels on both axes bold.\n"
                "Legend font: controls the size of the legend text.\n"
                "Legend position: lets you hide the legend or place it inside or outside the plot, including lower-right and right-side positions inside the chart.\n"
                "Theme: use the Theme menu above to change the overall application style."
            ),
            justify="left",
        ).pack(anchor="w")

    def _build_margins_tab(self, notebook):
        tab = ttk.Frame(notebook, padding=12)
        notebook.add(tab, text="Margins")
        tab.columnconfigure(0, weight=1)
        tab.columnconfigure(1, weight=1)

        frame_x = ttk.LabelFrame(tab, text="X-axis margins", style="Card.TLabelframe")
        frame_x.grid(row=0, column=0, sticky="nsew", padx=(0, 6), pady=(0, 8))
        self.x_left_margin = self._add_entry_row(frame_x, 0, "Left margin (Erlangs)", "50")
        self.x_right_margin = self._add_entry_row(frame_x, 1, "Right margin (Erlangs)", "50")
        self.x_margin_bar = self._add_entry_row(frame_x, 2, "Bar-plot X margin", "1.0")

        frame_y = ttk.LabelFrame(tab, text="Y-axis margins", style="Card.TLabelframe")
        frame_y.grid(row=0, column=1, sticky="nsew", padx=(6, 0), pady=(0, 8))
        self.y_bottom_margin_linear = self._add_entry_row(frame_y, 0, "Linear bottom margin", "0.01")
        self.y_top_margin_linear = self._add_entry_row(frame_y, 1, "Linear top margin", "0.01")
        self.y_bottom_margin_log = self._add_entry_row(frame_y, 2, "Log bottom factor", "0.8")
        self.y_top_margin_log = self._add_entry_row(frame_y, 3, "Log top factor", "1.2")

        frame_note = ttk.LabelFrame(tab, text="Notes", style="Card.TLabelframe")
        frame_note.grid(row=1, column=0, columnspan=2, sticky="nsew", pady=(0, 8))
        ttk.Label(
            frame_note,
            text=(
                "Left/Right margin: add horizontal padding around the plotted X range.\n"
                "Bar-plot X margin: adds extra horizontal spacing around the bars.\n"
                "Linear bottom/top margin: add vertical padding when the Y axis is linear.\n"
                "Log bottom/top factor: multiplies the lower and upper limits when the Y axis is logarithmic."
            ),
            justify="left",
        ).pack(anchor="w")

    def _add_entry_row(self, parent, row, label, default):
        ttk.Label(parent, text=label).grid(row=row, column=0, sticky="w", padx=(0, 8), pady=5)
        entry = ttk.Entry(parent, width=14)
        entry.grid(row=row, column=1, sticky="w", pady=5)
        entry.insert(0, default)
        return entry

    def _add_combo_row(self, parent, row, label, variable, values):
        ttk.Label(parent, text=label).grid(row=row, column=0, sticky="w", padx=(0, 8), pady=5)
        combo = ttk.Combobox(parent, textvariable=variable, values=values, state="readonly", width=34)
        combo.grid(row=row, column=1, sticky="w", pady=5)
        return combo

    def set_status(self, text: str):
        self.status_var.set(text)

    def parse_specific_loads(self, raw_text: str):
        raw = (raw_text or "").strip()
        if not raw:
            return None
        tokens = [tok.strip() for tok in raw.replace(";", ",").split(",") if tok.strip()]
        loads = set()
        for token in tokens:
            try:
                loads.add(int(token))
            except ValueError:
                raise ValueError(f"Invalid load value: {token}")
        return loads if loads else None

    def filter_series_by_loads(self, x, *arrays, selected_loads=None):
        if not selected_loads:
            return (x, *arrays)
        mask = np.array([int(v) in selected_loads for v in x], dtype=bool)
        filtered = [x[mask]]
        for arr in arrays:
            filtered.append(arr[mask])
        return tuple(filtered)

    # ------------------------------
    # Basic helpers
    # ------------------------------
    def get_algo_key(self, file_path: str) -> str:
        return os.path.splitext(os.path.basename(file_path))[0]

    def get_algo_label(self, file_path: str) -> str:
        key = self.get_algo_key(file_path)
        return self.algorithm_aliases.get(key, key)

    def get_metric_label(self, metric: str) -> str:
        lang = self.graph_language.get()
        return METRIC_LABELS.get(metric, {}).get(lang, metric)

    def get_algo_line_style(self, algo_key: str) -> dict:
        base = dict(ALGO_STYLES.get(algo_key, {"color": "#4c78a8", "marker": "o", "linestyle": "solid"}))
        base.update(self.custom_line_styles.get(algo_key, {}))
        marker = base.get("marker")
        if marker in {None, "None"}:
            base["marker"] = ""
        style_id = base.get("linestyle", "solid")
        if isinstance(style_id, list):
            style_id = tuple(style_id)
        if style_id in LINESTYLE_ID_TO_SPEC:
            base["linestyle_id"] = style_id
            base["linestyle"] = LINESTYLE_ID_TO_SPEC[style_id]
        elif isinstance(style_id, tuple):
            base["linestyle_id"] = next((sid for sid, spec in LINESTYLE_ID_TO_SPEC.items() if spec == style_id), "solid")
            base["linestyle"] = style_id
        elif style_id in {"-", "--", "-.", ":", ""}:
            reverse_map = {
                "-": "solid",
                "--": "dash",
                "-.": "dashdot",
                ":": "dot",
                "": "none",
            }
            sid = reverse_map.get(style_id, "solid")
            base["linestyle_id"] = sid
            base["linestyle"] = LINESTYLE_ID_TO_SPEC[sid]
        else:
            base["linestyle_id"] = "solid"
            base["linestyle"] = LINESTYLE_ID_TO_SPEC["solid"]
        return base

    def render_graph_text(self, key: str, **kwargs) -> str:
        lang = self.graph_language.get()
        template = self.custom_graph_texts.get(lang, {}).get(
            key, GRAPH_TEXT_DEFAULTS.get(lang, GRAPH_TEXT_DEFAULTS["en"]).get(key, "")
        )
        try:
            return template.format(**kwargs)
        except Exception:
            return template

    def get_component_label(self, component_keyword: str | None, component_metric: str | None = None, percent_mode: bool = False) -> str:
        if component_keyword is None:
            return component_metric or ""
        lang = self.graph_language.get()
        source = self.custom_percent_component_labels if percent_mode else self.custom_component_labels
        defaults = PERCENT_COMPONENT_LABEL_DEFAULTS if percent_mode else COMPONENT_LABEL_DEFAULTS
        return source.get(lang, {}).get(
            component_keyword,
            defaults.get(lang, defaults["en"]).get(component_keyword, component_keyword),
        )

    def add_configured_legend(self, ax, legend_font, *, handles=None, labels=None, count=None):
        position = self.legend_position.get()
        if position == "No legend":
            return None

        if count is None:
            if handles is not None:
                count = len(handles)
            elif labels is not None:
                count = len(labels)
            else:
                count = 0

        kwargs = {"fontsize": legend_font, "frameon": True}
        if handles is not None:
            kwargs["handles"] = handles
        if labels is not None:
            kwargs["labels"] = labels

        if position == "Inside (best)":
            kwargs.update({"loc": "best"})
        elif position == "Inside (upper right)":
            kwargs.update({"loc": "upper right"})
        elif position == "Inside (upper left)":
            kwargs.update({"loc": "upper left"})
        elif position == "Inside (lower right)":
            kwargs.update({"loc": "lower right"})
        elif position == "Inside (lower left)":
            kwargs.update({"loc": "lower left"})
        elif position == "Inside (center right)":
            kwargs.update({"loc": "center right"})
        elif position == "Inside (center left)":
            kwargs.update({"loc": "center left"})
        elif position == "Inside (upper center)":
            kwargs.update({"loc": "upper center"})
        elif position == "Inside (lower center)":
            kwargs.update({"loc": "lower center"})
        elif position == "Inside (center)":
            kwargs.update({"loc": "center"})
        elif position == "Bottom (outside)":
            kwargs.update({
                "loc": "upper center",
                "bbox_to_anchor": (0.5, -0.10),
                "ncol": max(1, min(4, count)),
            })
        elif position == "Top (outside)":
            kwargs.update({
                "loc": "lower center",
                "bbox_to_anchor": (0.5, 1.02),
                "ncol": max(1, min(4, count)),
            })
        elif position == "Right (outside)":
            kwargs.update({
                "loc": "center left",
                "bbox_to_anchor": (1.02, 0.5),
            })
        elif position == "Left (outside)":
            kwargs.update({
                "loc": "center right",
                "bbox_to_anchor": (-0.02, 0.5),
            })
        else:
            kwargs.update({"loc": "best"})

        leg = ax.legend(**kwargs)
        if leg is not None and leg.get_frame() is not None:
            leg.get_frame().set_edgecolor("black")
        return leg

    def get_axis_label_weight(self):
        return "bold" if bool(self.axis_text_bold.get()) else "normal"

    def get_tick_label_weight(self):
        return "bold" if bool(self.tick_text_bold.get()) else "normal"

    def apply_tick_label_weight(self, ax):
        tick_weight = self.get_tick_label_weight()
        for label in list(ax.get_xticklabels()) + list(ax.get_yticklabels()):
            label.set_fontweight(tick_weight)

    def update_files_panel(self):
        self.files_text.configure(state="normal")
        self.files_text.delete("1.0", tk.END)
        if not self.csv_files:
            self.files_text.insert(tk.END, "No CSV files loaded.")
        else:
            for file in self.csv_files:
                self.files_text.insert(tk.END, f"• {file}\n")
        self.files_text.configure(state="disabled")

    def get_available_csv_files(self):
        valid = []
        missing = []
        for file in self.csv_files:
            if os.path.isfile(file):
                valid.append(file)
            else:
                missing.append(file)

        if missing:
            messagebox.showwarning(
                'Warning',
                'The following files could not be found and will be ignored:\n\n' + '\n'.join(missing),
            )
            self.csv_files = valid
            self.update_files_panel()

        return valid

    def read_csv_safe(self, file_path: str):
        try:
            return pd.read_csv(file_path)
        except FileNotFoundError:
            return None
        except pd.errors.ParserError:
            try:
                return pd.read_csv(file_path, engine="python")
            except Exception as exc:
                messagebox.showwarning("Warning", f"Could not read file:\n{file_path}\n\n{exc}")
                return None
        except Exception as exc:
            messagebox.showwarning("Warning", f"Could not read file:\n{file_path}\n\n{exc}")
            return None

    def apply_component_selection_from_mapping(self, mapping: dict):
        for comp in self.components_available:
            if comp not in self.component_vars:
                self.component_vars[comp] = tk.BooleanVar(value=True)
            if comp in mapping:
                self.component_vars[comp].set(bool(mapping[comp]))

    def current_component_selection(self) -> dict:
        result = {}
        for comp in self.components_available:
            if comp not in self.component_vars:
                self.component_vars[comp] = tk.BooleanVar(value=True)
            result[comp] = bool(self.component_vars[comp].get())
        return result

    def finish_plot(self, fig, status_text="Plot generated."):
        self.last_fig = fig
        self.save_plot_button.configure(state="normal")
        fig.tight_layout(pad=2.0)
        self.set_status(status_text)
        plt.show()

    def get_log_error_mode_key(self) -> str:
        label = self.log_error_mode.get()
        mapping = {
            "Hide lower part when CI lower <= 0": "hide",
            "Compute interval in log scale": "log",
            "Mark truncated lower error": "mark",
        }
        return mapping.get(label, "mark")

    def get_log_plot_epsilon(self, values_valid):
        positive_values = values_valid[values_valid > 0]
        eps_base = float(np.min(positive_values)) if positive_values.size else 1e-12
        return max(eps_base * 0.1, 1e-12)

    def compute_interval_original(self, values_valid, alpha, n_boot, rng):
        mean = np.nanmean(values_valid, axis=1)
        rep_counts = np.sum(~np.isnan(values_valid), axis=1)
        std = np.nanstd(values_valid, axis=1, ddof=1)
        lower = np.empty(mean.shape[0], dtype=float)
        upper = np.empty(mean.shape[0], dtype=float)

        if self.ci_method.get() == "Bootstrap":
            for i_row, row in enumerate(values_valid):
                lo, up = bootstrap_ci_mean(row, alpha=alpha, n_boot=n_boot, rng=rng)
                lower[i_row] = lo
                upper[i_row] = up
        else:
            for i_row, (m, s, n_row) in enumerate(zip(mean, std, rep_counts)):
                if n_row < 2 or not np.isfinite(s):
                    lower[i_row] = m
                    upper[i_row] = m
                else:
                    t_val = t.ppf(1 - alpha / 2, df=int(n_row) - 1)
                    margin = t_val * s / np.sqrt(n_row)
                    lower[i_row] = m - margin
                    upper[i_row] = m + margin
        return mean, lower, upper

    def compute_interval_log_scale(self, values_valid, alpha, n_boot, rng):
        mean = np.full(values_valid.shape[0], np.nan, dtype=float)
        lower = np.full(values_valid.shape[0], np.nan, dtype=float)
        upper = np.full(values_valid.shape[0], np.nan, dtype=float)

        for i_row, row in enumerate(values_valid):
            row = np.asarray(row, dtype=float)
            row = row[np.isfinite(row) & (row > 0)]
            if row.size == 0:
                continue
            if row.size == 1:
                mean[i_row] = row[0]
                lower[i_row] = row[0]
                upper[i_row] = row[0]
                continue

            log_row = np.log10(row)
            if self.ci_method.get() == "Bootstrap":
                log_mean = float(np.mean(log_row))
                n = log_row.size
                samples = rng.choice(log_row, size=(n_boot, n), replace=True)
                boot_means = samples.mean(axis=1)
                lower_log = np.percentile(boot_means, 100 * (alpha / 2))
                upper_log = np.percentile(boot_means, 100 * (1 - alpha / 2))
            else:
                log_mean = float(np.mean(log_row))
                log_std = float(np.std(log_row, ddof=1))
                t_val = t.ppf(1 - alpha / 2, df=log_row.size - 1)
                margin = t_val * log_std / np.sqrt(log_row.size)
                lower_log = log_mean - margin
                upper_log = log_mean + margin

            mean[i_row] = 10 ** log_mean
            lower[i_row] = 10 ** lower_log
            upper[i_row] = 10 ** upper_log
        return mean, lower, upper

    def compute_series_statistics(self, df_m, algo_label, init_load, step, n_rep_user, alpha, n_boot, use_log_plot=False, selected_loads=None):
        rep_cols, n_rep_real = choose_rep_columns(df_m, n_rep_user, algo_label)
        if not rep_cols or n_rep_real < 2:
            return None

        df_m = parse_rep_values(df_m.copy(), rep_cols)
        values = df_m[rep_cols].values
        x_all = init_load + step * np.arange(len(df_m))
        valid = ~np.all(np.isnan(values) | (values == 0), axis=1)
        if not np.any(valid):
            return None

        values_valid = values[valid]
        x = x_all[valid]
        rng = np.random.default_rng(12345)
        log_mode_key = self.get_log_error_mode_key()

        if use_log_plot and log_mode_key == "log":
            mean, lower, upper = self.compute_interval_log_scale(values_valid, alpha, n_boot, rng)
        else:
            mean, lower, upper = self.compute_interval_original(values_valid, alpha, n_boot, rng)

        finite = np.isfinite(mean) & np.isfinite(lower) & np.isfinite(upper)
        if not np.any(finite):
            return None

        x = x[finite]
        mean = mean[finite]
        lower = lower[finite]
        upper = upper[finite]
        values_valid = values_valid[finite]

        if selected_loads:
            x, mean, lower, upper, values_valid = self.filter_series_by_loads(
                x, mean, lower, upper, values_valid, selected_loads=selected_loads
            )
            if len(x) == 0:
                return None

        truncated = np.zeros(mean.shape[0], dtype=bool)
        lower_plot = lower.copy()
        if use_log_plot and log_mode_key in {"hide", "mark"}:
            truncated = lower <= 0
            if log_mode_key == "hide":
                lower_plot = np.where(truncated, mean, lower)
            else:
                eps = self.get_log_plot_epsilon(values_valid)
                lower_plot = lower.copy()
                lower_plot[truncated] = eps

        upper_plot = upper.copy()
        yerr = np.vstack([
            np.maximum(mean - lower_plot, 0.0),
            np.maximum(upper_plot - mean, 0.0),
        ])

        return {
            "x": x,
            "mean": mean,
            "lower": lower,
            "upper": upper,
            "lower_plot": lower_plot,
            "upper_plot": upper_plot,
            "yerr": yerr,
            "truncated": truncated,
            "values_valid": values_valid,
        }

    def compute_mean_series(self, file_path, metric, init_load, step, n_rep_user, selected_loads=None):
        df = self.read_csv_safe(file_path)
        if df is None or "Metrics" not in df.columns:
            return None
        df_m = df[df["Metrics"] == metric].copy().reset_index(drop=True)
        if df_m.empty:
            return None

        rep_cols, _ = choose_rep_columns(df_m, n_rep_user, self.get_algo_label(file_path))
        if not rep_cols:
            return None

        df_m = parse_rep_values(df_m.copy(), rep_cols)
        values = df_m[rep_cols].values
        loads = init_load + step * np.arange(len(df_m))
        means = np.nanmean(values, axis=1)
        finite = np.isfinite(means)
        result = pd.DataFrame({"load": loads[finite], "mean": means[finite]})
        if selected_loads:
            result = result[result["load"].astype(int).isin(selected_loads)].copy()
        return result

    def print_ci_table_to_console(self, metric, algo_label, series):
        rows = []
        print("\n" + "=" * 92)
        print(f"CI TABLE | metric={metric} | algorithm={algo_label}")
        print("-" * 92)
        print(f"{'Load':>8} | {'Mean':>14} | {'CI lower':>14} | {'CI upper':>14} | {'Truncated':>10}")
        print("-" * 92)
        for load, mean, low, up, trunc in zip(
            series["x"], series["mean"], series["lower"], series["upper"], series["truncated"]
        ):
            row = {
                "metric": metric,
                "algorithm": algo_label,
                "load": int(load),
                "mean": float(mean),
                "ci_lower": float(low),
                "ci_upper": float(up),
                "truncated": bool(trunc),
            }
            rows.append(row)
            print(
                f"{row['load']:>8} | {format_float(row['mean']):>14} | {format_float(row['ci_lower']):>14} | {format_float(row['ci_upper']):>14} | {str(row['truncated']):>10}"
            )
        print("=" * 92)
        return rows

    def compute_best_by_load_rows(self, metric, best_candidates):
        rows = []
        for load in sorted(best_candidates):
            entries = [(algo, float(mean)) for algo, mean in best_candidates[load] if np.isfinite(mean)]
            if not entries:
                continue
            best_mean = min(mean for _algo, mean in entries)
            winners = sorted({algo for algo, mean in entries if np.isclose(mean, best_mean, rtol=1e-12, atol=1e-15)})
            rows.append({
                "metric": metric,
                "load": int(load),
                "best_algorithm": " / ".join(winners),
                "mean": float(best_mean),
            })
        return rows

    def print_best_by_load_table_to_console(self, metric, rows):
        if not rows:
            return
        print("\n" + "=" * 88)
        print(f"BEST ALGORITHM BY LOAD | metric={metric}")
        print("-" * 88)
        print(f"{'Load':>8} | {'Best algorithm':<56} | {'Mean':>14}")
        print("-" * 88)
        for row in rows:
            print(
                f"{row['load']:>8} | {row['best_algorithm']:<56} | {format_float(row['mean']):>14}"
            )
        print("=" * 88)

    def open_best_by_load_window(self, metric, rows):
        if not rows:
            return
        win = tk.Toplevel(self.root)
        win.title("Best algorithm by load")
        win.geometry("670x320")

        ttk.Label(
            win,
            text=f"Best algorithm by load for metric: {metric}",
            style="Subtitle.TLabel",
        ).pack(anchor="w", padx=12, pady=(12, 6))

        tree_frame = ttk.Frame(win)
        tree_frame.pack(fill="both", expand=True, padx=12, pady=(0, 12))
        columns = ("load", "best_algorithm", "mean")
        tree = ttk.Treeview(tree_frame, columns=columns, show="headings")
        headings = {
            "load": "Load",
            "best_algorithm": "Best algorithm",
            "mean": "Mean",
        }
        widths = {"load": 90, "best_algorithm": 400, "mean": 150}
        for col in columns:
            tree.heading(col, text=headings[col])
            tree.column(col, width=widths[col], anchor="center")
        yscroll = ttk.Scrollbar(tree_frame, orient="vertical", command=tree.yview)
        tree.configure(yscrollcommand=yscroll.set)
        tree.grid(row=0, column=0, sticky="nsew")
        yscroll.grid(row=0, column=1, sticky="ns")
        tree_frame.columnconfigure(0, weight=1)
        tree_frame.rowconfigure(0, weight=1)

        zebra = {"odd": "#ffffff", "even": "#f5f7fa"}
        tree.tag_configure("odd", background=zebra["odd"])
        tree.tag_configure("even", background=zebra["even"])

        for idx, row in enumerate(rows):
            tree.insert(
                "",
                "end",
                values=(
                    row["load"],
                    row["best_algorithm"],
                    format_float(row["mean"]),
                ),
                tags=(("even" if idx % 2 else "odd"),),
            )

        buttons = ttk.Frame(win, padding=12)
        buttons.pack(fill="x")
        ttk.Button(buttons, text="Close", command=win.destroy).pack(side="right")

    def export_last_ci_table(self):
        if not self.last_ci_rows:
            messagebox.showinfo("Info", "No CI table is available to export yet.")
            return
        path = filedialog.asksaveasfilename(
            title="Export CI table to CSV",
            defaultextension=".csv",
            filetypes=[("CSV files", "*.csv")],
        )
        if not path:
            return
        try:
            pd.DataFrame(self.last_ci_rows).to_csv(path, index=False, encoding="utf-8-sig")
            self.set_status(f"CI table exported to {path}")
        except Exception as exc:
            messagebox.showerror("Error", f"Could not export CI table.\n\n{exc}")

    def get_gain_metric_options(self):
        preferred = []
        others = []
        for metric in self.all_metrics:
            if is_gain_metric(metric):
                preferred.append(metric)
            else:
                others.append(metric)
        return preferred + others

    def export_gain_rows_to_csv(self, rows, metric, selected_algo):
        if not rows:
            messagebox.showinfo("Info", "No gain rows are available to export.")
            return
        path = filedialog.asksaveasfilename(
            title="Export gains to CSV",
            defaultextension=".csv",
            filetypes=[("CSV files", "*.csv")],
        )
        if not path:
            return
        try:
            df = pd.DataFrame(rows)
            df.insert(0, "selected_algorithm", selected_algo)
            df.insert(0, "metric", metric)
            df.to_csv(path, index=False, encoding="utf-8-sig")
            self.set_status(f"Gain table exported to {path}")
        except Exception as exc:
            messagebox.showerror("Error", f"Could not export gains.\n\n{exc}")

    # ------------------------------
    # Apply/Cancel editor windows
    # ------------------------------
    def open_components_window(self):
        if not self.components_available:
            messagebox.showinfo("Info", "No component metrics are currently available.")
            return

        win = tk.Toplevel(self.root)
        win.title("Select components for bar plot")
        win.geometry("780x520")
        win.grab_set()

        ttk.Label(win, text="Select which components to display:", font=("Segoe UI", 10, "bold")).pack(anchor="w", padx=12, pady=(12, 6))
        scroller = ScrollableWindow(win)
        temp_vars = {}
        for comp in self.components_available:
            if comp not in self.component_vars:
                self.component_vars[comp] = tk.BooleanVar(value=True)
            temp_vars[comp] = tk.BooleanVar(value=bool(self.component_vars[comp].get()))
            kw = match_component_keyword(comp)
            label = self.get_component_label(kw, comp, percent_mode=False) if kw else comp
            ttk.Checkbutton(scroller.inner, text=f"{label} ({comp})", variable=temp_vars[comp]).pack(anchor="w", padx=12, pady=2)

        buttons = ttk.Frame(win, padding=12)
        buttons.pack(fill="x")

        def apply_changes():
            for comp, var in temp_vars.items():
                self.component_vars[comp].set(bool(var.get()))
            self.set_status("Component selection updated.")
            win.destroy()

        ttk.Button(buttons, text="Apply", command=apply_changes).pack(side="right", padx=(6, 0))
        ttk.Button(buttons, text="Cancel", command=win.destroy).pack(side="right")

    def open_algorithm_names_window(self):
        files = self.get_available_csv_files()
        if not files:
            return

        win = tk.Toplevel(self.root)
        win.title("Configure algorithm names")
        win.geometry("720x420")
        win.grab_set()
        ttk.Label(win, text="Set the display name for each algorithm:", font=("Segoe UI", 10, "bold")).pack(anchor="w", padx=12, pady=(12, 6))
        container = ScrollableWindow(win)
        entries = {}
        for file in files:
            algo_key = self.get_algo_key(file)
            if algo_key not in self.algorithm_aliases:
                self.algorithm_aliases[algo_key] = algo_key
            row = ttk.Frame(container.inner)
            row.pack(fill="x", padx=12, pady=4)
            ttk.Label(row, text=algo_key, width=24).pack(side="left")
            entry = ttk.Entry(row)
            entry.insert(0, self.algorithm_aliases.get(algo_key, algo_key))
            entry.pack(side="left", fill="x", expand=True, padx=(8, 0))
            entries[algo_key] = entry

        buttons = ttk.Frame(win, padding=12)
        buttons.pack(fill="x")

        def apply_changes():
            for key, entry in entries.items():
                value = entry.get().strip()
                self.algorithm_aliases[key] = value if value else key
            self.set_status("Algorithm names updated.")
            win.destroy()

        ttk.Button(buttons, text="Apply", command=apply_changes).pack(side="right", padx=(6, 0))
        ttk.Button(buttons, text="Cancel", command=win.destroy).pack(side="right")

    def open_graph_texts_window(self):
        lang = self.graph_language.get()
        win = tk.Toplevel(self.root)
        win.title("Edit graph texts")
        win.geometry("900x620")
        win.grab_set()
        ttk.Label(win, text=f"Edit graph texts for language: {lang}", font=("Segoe UI", 10, "bold")).pack(anchor="w", padx=12, pady=(12, 6))
        ttk.Label(win, text="You can use {metric} and {load} inside text templates.").pack(anchor="w", padx=12, pady=(0, 6))
        container = ScrollableWindow(win)
        entries = {}
        for key, label in GRAPH_TEXT_KEYS:
            row = ttk.Frame(container.inner)
            row.pack(fill="x", padx=12, pady=4)
            ttk.Label(row, text=label, width=34).pack(side="left")
            entry = ttk.Entry(row)
            entry.insert(0, self.custom_graph_texts[lang].get(key, GRAPH_TEXT_DEFAULTS[lang][key]))
            entry.pack(side="left", fill="x", expand=True, padx=(8, 0))
            entries[key] = entry

        buttons = ttk.Frame(win, padding=12)
        buttons.pack(fill="x")

        def apply_changes():
            for key, entry in entries.items():
                self.custom_graph_texts[lang][key] = entry.get()
            self.set_status(f"Graph texts updated for language '{lang}'.")
            win.destroy()

        ttk.Button(buttons, text="Apply", command=apply_changes).pack(side="right", padx=(6, 0))
        ttk.Button(buttons, text="Cancel", command=win.destroy).pack(side="right")

    def open_line_styles_window(self):
        files = self.get_available_csv_files()
        if not files:
            messagebox.showerror("Error", "Load CSV files before editing line styles.")
            return

        win = tk.Toplevel(self.root)
        win.title("Edit line styles")
        win.grab_set()
        win.geometry("1120x640")
        win.minsize(1040, 600)

        ttk.Label(
            win,
            text="Choose the line color, marker, and line style for each algorithm. The preview updates immediately.",
            style="Subtitle.TLabel",
        ).pack(fill="x", padx=12, pady=(12, 8))

        container = ScrollableWindow(win)
        table = ttk.Frame(container.inner, padding=(12, 4, 12, 4))
        table.pack(fill="both", expand=True)
        for col in range(6):
            table.columnconfigure(col, weight=1)

        headers = ["Algorithm", "Display name", "Color", "Marker", "Line style", "Preview"]
        for col, header in enumerate(headers):
            ttk.Label(table, text=header, style="Subtitle.TLabel").grid(
                row=0, column=col, sticky="w", padx=4, pady=(0, 6)
            )

        rows = {}
        marker_labels = [label for _, label in MARKER_OPTIONS]
        linestyle_labels = [label for style_id, label, _mpl, _dash in LINESTYLE_OPTIONS]

        def apply_color_preview(color_var, entry_widget, swatch_widget):
            color_text = color_var.get().strip() or "#4c78a8"
            try:
                entry_widget.configure(fg=color_text, insertbackground=color_text)
                swatch_widget.configure(bg=color_text)
            except tk.TclError:
                entry_widget.configure(fg="black", insertbackground="black")
                swatch_widget.configure(bg="#d9d9d9")

        def pick_color(color_var):
            chosen = colorchooser.askcolor(color=color_var.get() or "#4c78a8", parent=win)[1]
            if chosen:
                color_var.set(chosen)

        def draw_marker_preview(canvas, x, y, marker, color):
            if marker in {"", None}:
                return
            if marker == "o":
                canvas.create_oval(x-4, y-4, x+4, y+4, fill=color, outline=color)
            elif marker == "s":
                canvas.create_rectangle(x-4, y-4, x+4, y+4, fill=color, outline=color)
            elif marker == "^":
                canvas.create_polygon(x, y-5, x-5, y+4, x+5, y+4, fill=color, outline=color)
            elif marker == "v":
                canvas.create_polygon(x-5, y-4, x+5, y-4, x, y+5, fill=color, outline=color)
            elif marker == "D":
                canvas.create_polygon(x, y-5, x-5, y, x, y+5, x+5, y, fill=color, outline=color)
            elif marker == "P":
                canvas.create_rectangle(x-1, y-5, x+1, y+5, fill=color, outline=color)
                canvas.create_rectangle(x-5, y-1, x+5, y+1, fill=color, outline=color)
            elif marker == "X":
                canvas.create_line(x-5, y-5, x+5, y+5, fill=color, width=2)
                canvas.create_line(x-5, y+5, x+5, y-5, fill=color, width=2)
            elif marker == "*":
                canvas.create_text(x, y, text="★", fill=color, font=("Segoe UI Symbol", 11))
            elif marker == "+":
                canvas.create_line(x, y-5, x, y+5, fill=color, width=2)
                canvas.create_line(x-5, y, x+5, y, fill=color, width=2)
            elif marker == "x":
                canvas.create_line(x-4, y-4, x+4, y+4, fill=color, width=2)
                canvas.create_line(x-4, y+4, x+4, y-4, fill=color, width=2)
            elif marker == "h":
                canvas.create_polygon(x-5, y, x-3, y-4, x+3, y-4, x+5, y, x+3, y+4, x-3, y+4, fill=color, outline=color)
            else:
                canvas.create_oval(x-4, y-4, x+4, y+4, fill=color, outline=color)

        def update_preview(canvas, color_var, marker_var, linestyle_var):
            canvas.delete("all")
            color = color_var.get().strip() or "#4c78a8"
            marker = MARKER_LABEL_TO_VALUE.get(marker_var.get(), "")
            linestyle_id = LINESTYLE_LABEL_TO_ID.get(linestyle_var.get(), "solid")
            dash = LINESTYLE_ID_TO_CANVAS_DASH.get(linestyle_id)
            canvas.create_line(12, 18, 158, 18, fill=color, width=2, dash=dash)
            draw_marker_preview(canvas, 85, 18, marker, color)

        for idx, file in enumerate(files, start=1):
            algo_key = self.get_algo_key(file)
            style = self.get_algo_line_style(algo_key)
            color_var = tk.StringVar(value=style.get("color", "#4c78a8"))
            marker_var = tk.StringVar(value=MARKER_VALUE_TO_LABEL.get(style.get("marker", ""), "None"))
            linestyle_var = tk.StringVar(value=LINESTYLE_ID_TO_LABEL.get(style.get("linestyle_id", "solid"), "Solid (-)"))

            ttk.Label(table, text=algo_key).grid(row=idx, column=0, sticky="w", padx=4, pady=4)
            ttk.Label(table, text=self.get_algo_label(file)).grid(row=idx, column=1, sticky="w", padx=4, pady=4)

            color_frame = ttk.Frame(table)
            color_frame.grid(row=idx, column=2, sticky="ew", padx=4, pady=4)
            swatch = tk.Label(color_frame, width=2, relief="solid", bd=1, bg=color_var.get() or "#4c78a8")
            swatch.pack(side="left", padx=(0, 6))
            color_entry = tk.Entry(color_frame, textvariable=color_var, width=14, relief="solid", bd=1)
            color_entry.pack(side="left", fill="x", expand=True)
            apply_color_preview(color_var, color_entry, swatch)
            ttk.Button(color_frame, text="Pick", command=lambda v=color_var: pick_color(v), style="Primary.TButton").pack(side="left", padx=(6, 0))

            marker_combo = ttk.Combobox(table, values=marker_labels, textvariable=marker_var, state="readonly", width=17)
            marker_combo.grid(row=idx, column=3, sticky="ew", padx=4, pady=4)
            linestyle_combo = ttk.Combobox(table, values=linestyle_labels, textvariable=linestyle_var, state="readonly", width=20)
            linestyle_combo.grid(row=idx, column=4, sticky="ew", padx=4, pady=4)

            preview = tk.Canvas(table, width=170, height=36, bd=1, relief="solid", highlightthickness=0, bg="#ffffff")
            preview.grid(row=idx, column=5, sticky="ew", padx=4, pady=4)

            def _refresh_preview(*_args, canvas=preview, cv=color_var, mv=marker_var, lv=linestyle_var, entry=color_entry, patch=swatch):
                apply_color_preview(cv, entry, patch)
                update_preview(canvas, cv, mv, lv)

            color_var.trace_add("write", _refresh_preview)
            marker_var.trace_add("write", _refresh_preview)
            linestyle_var.trace_add("write", _refresh_preview)
            _refresh_preview()

            rows[algo_key] = {"color": color_var, "marker": marker_var, "linestyle": linestyle_var}

        buttons = ttk.Frame(win, padding=(12, 8, 12, 12))
        buttons.pack(fill="x")

        def apply_changes():
            for algo_key, vars_map in rows.items():
                chosen_color = vars_map["color"].get().strip() or self.get_algo_line_style(algo_key).get("color", "#4c78a8")
                self.custom_line_styles.setdefault(algo_key, {})
                self.custom_line_styles[algo_key]["color"] = chosen_color
                self.custom_line_styles[algo_key]["marker"] = MARKER_LABEL_TO_VALUE.get(vars_map["marker"].get(), "")
                self.custom_line_styles[algo_key]["linestyle"] = LINESTYLE_LABEL_TO_ID.get(vars_map["linestyle"].get(), "solid")
            self.set_status("Line styles updated for line plots.")
            win.destroy()

        ttk.Button(buttons, text="Apply", command=apply_changes, style="Primary.TButton").pack(side="right", padx=(6, 0))
        ttk.Button(buttons, text="Cancel", command=win.destroy, style="Primary.TButton").pack(side="right")

    def open_component_legends_window(self):
        lang = self.graph_language.get()
        win = tk.Toplevel(self.root)
        win.title("Edit component legends")
        win.geometry("880x520")
        win.grab_set()
        ttk.Label(win, text=f"Edit component legends for language: {lang}", font=("Segoe UI", 10, "bold")).pack(anchor="w", padx=12, pady=(12, 6))
        container = ScrollableWindow(win)

        header = ttk.Frame(container.inner)
        header.pack(fill="x", padx=12, pady=(0, 4))
        ttk.Label(header, text="Keyword", width=28).pack(side="left")
        ttk.Label(header, text="Absolute stacked legend", width=26).pack(side="left")
        ttk.Label(header, text="100% stacked legend", width=26).pack(side="left")

        abs_entries = {}
        pct_entries = {}
        for kw in COMPONENT_KEYWORDS:
            row = ttk.Frame(container.inner)
            row.pack(fill="x", padx=12, pady=3)
            ttk.Label(row, text=kw, width=28).pack(side="left")
            e_abs = ttk.Entry(row, width=26)
            e_abs.insert(0, self.custom_component_labels[lang].get(kw, COMPONENT_LABEL_DEFAULTS[lang].get(kw, kw)))
            e_abs.pack(side="left", padx=(4, 8))
            e_pct = ttk.Entry(row, width=26)
            e_pct.insert(0, self.custom_percent_component_labels[lang].get(kw, PERCENT_COMPONENT_LABEL_DEFAULTS[lang].get(kw, kw)))
            e_pct.pack(side="left")
            abs_entries[kw] = e_abs
            pct_entries[kw] = e_pct

        buttons = ttk.Frame(win, padding=12)
        buttons.pack(fill="x")

        def apply_changes():
            for kw, entry in abs_entries.items():
                value = entry.get().strip()
                self.custom_component_labels[lang][kw] = value if value else COMPONENT_LABEL_DEFAULTS[lang].get(kw, kw)
            for kw, entry in pct_entries.items():
                value = entry.get().strip()
                self.custom_percent_component_labels[lang][kw] = value if value else PERCENT_COMPONENT_LABEL_DEFAULTS[lang].get(kw, kw)
            self.set_status(f"Component legends updated for language '{lang}'.")
            win.destroy()

        ttk.Button(buttons, text="Apply", command=apply_changes).pack(side="right", padx=(6, 0))
        ttk.Button(buttons, text="Cancel", command=win.destroy).pack(side="right")


    def open_gain_window(self):
        files = self.get_available_csv_files()
        if len(files) < 2:
            messagebox.showinfo("Info", "Load at least two CSV files to compute gains.")
            return

        gain_metrics = self.get_gain_metric_options()
        if not gain_metrics:
            messagebox.showinfo("Info", "No metrics were found for gain computation.")
            return

        preferred_metric = self.metric_var.get().strip() if self.metric_var.get().strip() in gain_metrics else next((m for m in gain_metrics if is_gain_metric(m)), gain_metrics[0])

        win = tk.Toplevel(self.root)
        win.title("Compute gains")
        win.geometry("1120x680")
        win.grab_set()

        controls = ttk.LabelFrame(win, text="Gain setup", style="Card.TLabelframe")
        controls.pack(fill="x", padx=12, pady=12)
        controls.columnconfigure(1, weight=1)
        controls.columnconfigure(3, weight=1)

        selected_algo = tk.StringVar(value=self.get_algo_label(files[0]))
        selected_metric = tk.StringVar(value=preferred_metric)
        gain_load_filter = tk.StringVar(value=self.load_filter.get().strip() if hasattr(self, "load_filter") else "")
        summary_var = tk.StringVar(value="Choose an algorithm and click Compute.")
        exported_rows = []

        ttk.Label(controls, text="Selected algorithm").grid(row=0, column=0, sticky="w", pady=4, padx=(0, 8))
        ttk.Combobox(
            controls,
            textvariable=selected_algo,
            values=[self.get_algo_label(f) for f in files],
            state="readonly",
        ).grid(row=0, column=1, sticky="ew", pady=4)
        ttk.Label(controls, text="Metric").grid(row=0, column=2, sticky="w", pady=4, padx=(12, 8))
        ttk.Combobox(
            controls,
            textvariable=selected_metric,
            values=gain_metrics,
            state="readonly",
        ).grid(row=0, column=3, sticky="ew", pady=4)
        ttk.Label(controls, text="Specific loads").grid(row=1, column=0, sticky="w", pady=4, padx=(0, 8))
        ttk.Entry(controls, textvariable=gain_load_filter).grid(row=1, column=1, sticky="ew", pady=4)
        ttk.Label(controls, text="Example: 500, 1000, 1500", style="Subtitle.TLabel").grid(row=1, column=2, columnspan=2, sticky="w", pady=4, padx=(12, 0))

        ttk.Label(
            controls,
            text="Gain (%) = 100 × (Rother - Ralgo) / Rother. Rother: comparison algorithm. Ralgo: selected algorithm.",
            style="Subtitle.TLabel",
        ).grid(row=2, column=0, columnspan=4, sticky="w", pady=(6, 0))

        ttk.Label(win, textvariable=summary_var, style="Subtitle.TLabel").pack(anchor="w", padx=12)

        tree_frame = ttk.Frame(win)
        tree_frame.pack(fill="both", expand=True, padx=12, pady=12)
        columns = ("other", "load", "r_other", "r_selected", "gain")
        tree = ttk.Treeview(tree_frame, columns=columns, show="headings")

        zebra = {
            "odd": "#ffffff",
            "even": "#f5f7fa",
        }
        tree.tag_configure("positive_odd", foreground="#1f7a1f", background=zebra["odd"])
        tree.tag_configure("positive_even", foreground="#1f7a1f", background=zebra["even"])
        tree.tag_configure("negative_odd", foreground="#c62828", background=zebra["odd"])
        tree.tag_configure("negative_even", foreground="#c62828", background=zebra["even"])
        tree.tag_configure("zero_odd", foreground="#616161", background=zebra["odd"])
        tree.tag_configure("zero_even", foreground="#616161", background=zebra["even"])
        tree.tag_configure("undefined_odd", foreground="#9aa5b1", background=zebra["odd"])
        tree.tag_configure("undefined_even", foreground="#9aa5b1", background=zebra["even"])

        headings = {
            "other": "Other algorithm",
            "load": "Load",
            "r_other": "Rother",
            "r_selected": "Ralgo",
            "gain": "Gain (%)",
        }
        widths = {"other": 220, "load": 90, "r_other": 170, "r_selected": 170, "gain": 120}
        for col in columns:
            tree.heading(col, text=headings[col])
            tree.column(col, width=widths[col], anchor="center")
        yscroll = ttk.Scrollbar(tree_frame, orient="vertical", command=tree.yview)
        xscroll = ttk.Scrollbar(tree_frame, orient="horizontal", command=tree.xview)
        tree.configure(yscrollcommand=yscroll.set, xscrollcommand=xscroll.set)
        tree.grid(row=0, column=0, sticky="nsew")
        yscroll.grid(row=0, column=1, sticky="ns")
        xscroll.grid(row=1, column=0, sticky="ew")
        tree_frame.columnconfigure(0, weight=1)
        tree_frame.rowconfigure(0, weight=1)

        def row_tag(sign_tag: str, index: int) -> str:
            return f"{sign_tag}_{'even' if index % 2 else 'odd'}"

        def compute_gains():
            nonlocal exported_rows
            exported_rows = []
            tree.delete(*tree.get_children())
            algo_map = {self.get_algo_label(f): f for f in files}
            selected_file = algo_map.get(selected_algo.get())
            if selected_file is None:
                messagebox.showerror("Error", "Could not resolve the selected algorithm.")
                return

            try:
                init_load = int(self.init_load.get())
                step = int(self.load_step.get())
                n_rep_user = int(self.n_rep.get())
                selected_loads = self.parse_specific_loads(gain_load_filter.get())
            except ValueError as exc:
                messagebox.showerror("Error", str(exc))
                return

            target_series = self.compute_mean_series(selected_file, selected_metric.get(), init_load, step, n_rep_user, selected_loads=selected_loads)
            if target_series is None or target_series.empty:
                messagebox.showerror("Error", "The selected algorithm has no valid series for the chosen metric.")
                return

            comparison_count = 0
            visual_row_index = 0
            for other_file in files:
                if other_file == selected_file:
                    continue
                other_label = self.get_algo_label(other_file)
                other_series = self.compute_mean_series(other_file, selected_metric.get(), init_load, step, n_rep_user, selected_loads=selected_loads)
                if other_series is None or other_series.empty:
                    continue
                merged = other_series.merge(target_series, on="load", suffixes=("_other", "_selected"))
                if merged.empty:
                    continue
                merged["gain"] = np.where(
                    merged["mean_other"] != 0,
                    (merged["mean_other"] - merged["mean_selected"]) / merged["mean_other"] * 100.0,
                    np.nan,
                )
                comparison_count += 1
                for _, row in merged.iterrows():
                    gain_value = float(row["gain"]) if np.isfinite(row["gain"]) else np.nan
                    gain_text = "undefined" if not np.isfinite(gain_value) else f"{gain_value:.2f}%"
                    record = {
                        "other_algorithm": other_label,
                        "load": int(row["load"]),
                        "r_other": float(row["mean_other"]),
                        "r_selected": float(row["mean_selected"]),
                        "gain_percent": gain_value,
                    }
                    exported_rows.append(record)
                    if not np.isfinite(gain_value):
                        sign_tag = "undefined"
                    elif gain_value > 0:
                        sign_tag = "positive"
                    elif gain_value < 0:
                        sign_tag = "negative"
                    else:
                        sign_tag = "zero"
                    tree.insert(
                        "",
                        "end",
                        values=(
                            other_label,
                            int(row["load"]),
                            format_float(row["mean_other"]),
                            format_float(row["mean_selected"]),
                            gain_text,
                        ),
                        tags=(row_tag(sign_tag, visual_row_index),),
                    )
                    visual_row_index += 1

            if not exported_rows:
                summary_var.set("No valid comparisons could be computed.")
                return

            summary_var.set(f"Computed {len(exported_rows)} rows across {comparison_count} comparison algorithm(s).")
            print(f"GAIN TABLE | metric={selected_metric.get()} | selected algorithm={selected_algo.get()}")
            print("-" * 112)
            print(f"{'Other algorithm':<24} | {'Load':>8} | {'Rother':>14} | {'Ralgo':>14} | {'Gain (%)':>12}")
            print("-" * 112)
            for item in tree.get_children():
                other, load, r_other, r_selected, gain = tree.item(item, "values")
                print(f"{other:<24} | {str(load):>8} | {str(r_other):>14} | {str(r_selected):>14} | {str(gain):>12}")
            print("=" * 112)
            self.set_status(f"Gain table computed for '{selected_algo.get()}' using metric '{selected_metric.get()}'.")

        buttons = ttk.Frame(win, padding=12)
        buttons.pack(fill="x")
        ttk.Button(
            buttons,
            text="Export CSV",
            style="Primary.TButton",
            command=lambda: self.export_gain_rows_to_csv(exported_rows, selected_metric.get(), selected_algo.get()),
        ).pack(side="left")
        ttk.Button(buttons, text="Close", command=win.destroy).pack(side="right")
        ttk.Button(buttons, text="Compute", command=compute_gains, style="Compute.TButton").pack(side="right", padx=(0, 6))

    # ------------------------------
    # Save / load customizations and plots
    # ------------------------------
    def _safe_entry_value(self, entry_widget):
        try:
            return entry_widget.get().strip()
        except Exception:
            return ""

    def _set_entry_if_present(self, attr_name: str, value):
        widget = getattr(self, attr_name, None)
        if widget is None or value is None:
            return False
        try:
            widget.delete(0, tk.END)
            widget.insert(0, str(value))
            return True
        except Exception:
            return False

    def _sanitize_line_styles_for_save(self):
        sanitized = {}
        for key, style_map in self.custom_line_styles.items():
            if not isinstance(style_map, dict):
                continue
            color = str(style_map.get("color", self.get_algo_line_style(str(key)).get("color", "#4c78a8")))
            marker = style_map.get("marker", self.get_algo_line_style(str(key)).get("marker", ""))
            linestyle = style_map.get("linestyle", self.get_algo_line_style(str(key)).get("linestyle_id", "solid"))
            if marker in {None, "None"}:
                marker = ""
            if linestyle in {None, "None", ""}:
                linestyle = "none"
            elif isinstance(linestyle, str) and linestyle in {"-", "--", "-.", ":", ""}:
                reverse_map = {"-": "solid", "--": "dash", "-.": "dashdot", ":": "dot", "": "none"}
                linestyle = reverse_map.get(linestyle, "solid")
            elif not (isinstance(linestyle, str) and linestyle in LINESTYLE_ID_TO_SPEC):
                linestyle = "solid"
            sanitized[str(key)] = {
                "color": color,
                "marker": marker,
                "linestyle": linestyle,
            }
        return sanitized

    def _build_customizations_payload(self):
        return {
            "schema_version": 2,
            "app": "Simulation Graph Analyzer",
            "settings": {
                "general": {
                    "theme_name": self.theme_name.get(),
                    "graph_language": self.graph_language.get(),
                    "plot_type": self.plot_type.get(),
                    "log_scale": bool(self.log_scale.get()),
                    "metric": self.metric_var.get(),
                },
                "plot": {
                    "bar_plot_mode": self.bar_plot_mode.get(),
                    "log_error_mode": self.log_error_mode.get(),
                    "y_grid_mode": self.y_grid_mode.get(),
                    "legend_position": self.legend_position.get(),
                },
                "statistics": {
                    "confidence_level": self.conf_level.get(),
                    "ci_method": self.ci_method.get(),
                    "bootstrap_resamples": self._safe_entry_value(self.bootstrap_n),
                },
                "loads": {
                    "init_load": self._safe_entry_value(self.init_load),
                    "load_step": self._safe_entry_value(self.load_step),
                    "replications": self._safe_entry_value(self.n_rep),
                    "bar_load_point": self._safe_entry_value(self.bar_load_point),
                    "load_filter": self._safe_entry_value(self.load_filter) if hasattr(self, "load_filter") else "",
                },
                "appearance": {
                    "axis_font": self._safe_entry_value(self.axis_font),
                    "tick_font": self._safe_entry_value(self.tick_font),
                    "legend_font": self._safe_entry_value(self.legend_font),
                    "axis_text_bold": bool(self.axis_text_bold.get()),
                    "tick_text_bold": bool(self.tick_text_bold.get()),
                },
                "margins": {
                    "x_left_margin": self._safe_entry_value(self.x_left_margin),
                    "x_right_margin": self._safe_entry_value(self.x_right_margin),
                    "x_margin_bar": self._safe_entry_value(self.x_margin_bar),
                    "y_bottom_margin_linear": self._safe_entry_value(self.y_bottom_margin_linear),
                    "y_top_margin_linear": self._safe_entry_value(self.y_top_margin_linear),
                    "y_bottom_margin_log": self._safe_entry_value(self.y_bottom_margin_log),
                    "y_top_margin_log": self._safe_entry_value(self.y_top_margin_log),
                },
                "algorithm_aliases": {str(k): str(v) for k, v in self.algorithm_aliases.items()},
                "line_styles": self._sanitize_line_styles_for_save(),
                "graph_texts": deepcopy(self.custom_graph_texts),
                "component_labels": deepcopy(self.custom_component_labels),
                "percent_component_labels": deepcopy(self.custom_percent_component_labels),
                "component_selection": self.current_component_selection(),
            },
        }

    def save_customizations(self):
        path = filedialog.asksaveasfilename(
            title="Save customizations",
            defaultextension=".json",
            filetypes=[("JSON files", "*.json")],
        )
        if not path:
            return

        payload = self._build_customizations_payload()
        try:
            with open(path, "w", encoding="utf-8") as f:
                json.dump(payload, f, ensure_ascii=False, indent=2, sort_keys=True)
            self.set_status(f"Customizations saved to {path}")
            messagebox.showinfo("Customizations saved", f"The user settings were saved successfully.\n\nFile: {path}")
        except Exception as exc:
            messagebox.showerror("Error", f"Could not save customizations.\n\n{exc}")

    def load_customizations(self):
        path = filedialog.askopenfilename(
            title="Load customizations",
            filetypes=[("JSON files", "*.json")],
        )
        if not path:
            return
        try:
            with open(path, "r", encoding="utf-8") as f:
                payload = json.load(f)
        except Exception as exc:
            messagebox.showerror("Error", f"Could not load customizations.\n\n{exc}")
            return

        settings = payload.get("settings") if isinstance(payload, dict) and isinstance(payload.get("settings"), dict) else None
        if settings is None:
            settings = {
                "general": {
                    "theme_name": payload.get("theme_name"),
                    "graph_language": payload.get("graph_language"),
                    "plot_type": payload.get("plot_type"),
                    "log_scale": payload.get("log_scale"),
                    "metric": payload.get("metric"),
                },
                "plot": {
                    "bar_plot_mode": payload.get("bar_plot_mode"),
                    "log_error_mode": payload.get("log_error_mode"),
                    "y_grid_mode": payload.get("y_grid_mode"),
                    "legend_position": payload.get("legend_position"),
                },
                "statistics": {
                    "confidence_level": payload.get("conf_level") or payload.get("confidence_level"),
                    "ci_method": payload.get("ci_method"),
                    "bootstrap_resamples": payload.get("bootstrap_n"),
                },
                "loads": {
                    "init_load": payload.get("init_load"),
                    "load_step": payload.get("load_step"),
                    "replications": payload.get("n_rep") or payload.get("replications"),
                    "bar_load_point": payload.get("bar_load_point"),
                    "load_filter": payload.get("load_filter"),
                },
                "appearance": {
                    "axis_font": payload.get("axis_font"),
                    "tick_font": payload.get("tick_font"),
                    "legend_font": payload.get("legend_font"),
                    "axis_text_bold": payload.get("axis_text_bold"),
                    "tick_text_bold": payload.get("tick_text_bold"),
                },
                "margins": {
                    "x_left_margin": payload.get("x_left_margin"),
                    "x_right_margin": payload.get("x_right_margin"),
                    "x_margin_bar": payload.get("x_margin_bar"),
                    "y_bottom_margin_linear": payload.get("y_bottom_margin_linear"),
                    "y_top_margin_linear": payload.get("y_top_margin_linear"),
                    "y_bottom_margin_log": payload.get("y_bottom_margin_log"),
                    "y_top_margin_log": payload.get("y_top_margin_log"),
                },
                "algorithm_aliases": payload.get("algorithm_aliases"),
                "line_styles": payload.get("line_styles"),
                "graph_texts": payload.get("graph_texts"),
                "component_labels": payload.get("component_labels"),
                "percent_component_labels": payload.get("percent_component_labels"),
                "component_selection": payload.get("component_selection"),
            }

        applied = []
        skipped = []

        general = settings.get("general", {}) if isinstance(settings.get("general"), dict) else {}
        plot = settings.get("plot", {}) if isinstance(settings.get("plot"), dict) else {}
        statistics = settings.get("statistics", {}) if isinstance(settings.get("statistics"), dict) else {}
        loads = settings.get("loads", {}) if isinstance(settings.get("loads"), dict) else {}
        appearance = settings.get("appearance", {}) if isinstance(settings.get("appearance"), dict) else {}
        margins = settings.get("margins", {}) if isinstance(settings.get("margins"), dict) else {}

        def set_stringvar_if_valid(var, value, allowed, label):
            if not isinstance(value, str):
                return
            if value in allowed:
                var.set(value)
                applied.append(label)
            else:
                skipped.append(label)

        def set_boolvar(var, value, label):
            if isinstance(value, bool):
                var.set(value)
                applied.append(label)
            elif isinstance(value, (int, float)) and value in {0, 1}:
                var.set(bool(value))
                applied.append(label)

        set_stringvar_if_valid(self.theme_name, general.get("theme_name"), set(THEME_PRESETS.keys()), "theme")
        set_stringvar_if_valid(self.graph_language, general.get("graph_language"), {"en", "pt"}, "graph language")
        set_stringvar_if_valid(self.plot_type, general.get("plot_type"), {"line", "bar"}, "plot type")
        set_boolvar(self.log_scale, general.get("log_scale"), "log scale")

        metric_value = general.get("metric")
        if isinstance(metric_value, str) and metric_value.strip():
            if metric_value in self.all_metrics:
                self.metric_var.set(metric_value)
                applied.append("metric")
            else:
                self.pending_metric_from_customizations = metric_value
                skipped.append("metric (will be applied after loading matching CSV files)")

        set_stringvar_if_valid(self.bar_plot_mode, plot.get("bar_plot_mode"), {"absolute", "percent"}, "bar plot mode")
        set_stringvar_if_valid(self.log_error_mode, plot.get("log_error_mode"), set(LOG_ERROR_MODE_OPTIONS), "log error mode")
        set_stringvar_if_valid(self.y_grid_mode, plot.get("y_grid_mode"), set(Y_GRID_OPTIONS), "Y grid mode")
        set_stringvar_if_valid(self.legend_position, plot.get("legend_position"), set(LEGEND_POSITION_OPTIONS), "legend position")

        set_stringvar_if_valid(self.conf_level, statistics.get("confidence_level"), {"90%", "95%", "99%"}, "confidence level")
        set_stringvar_if_valid(self.ci_method, statistics.get("ci_method"), {"t-Student", "Bootstrap"}, "CI method")
        if self._set_entry_if_present("bootstrap_n", statistics.get("bootstrap_resamples")):
            applied.append("bootstrap resamples")

        for attr_name, key_name, label in [
            ("init_load", "init_load", "initial load"),
            ("load_step", "load_step", "load increment"),
            ("n_rep", "replications", "replications"),
            ("bar_load_point", "bar_load_point", "bar plot load point"),
            ("load_filter", "load_filter", "specific loads filter"),
        ]:
            if self._set_entry_if_present(attr_name, loads.get(key_name)):
                applied.append(label)

        for attr_name, key_name, label in [
            ("axis_font", "axis_font", "axis font"),
            ("tick_font", "tick_font", "tick font"),
            ("legend_font", "legend_font", "legend font"),
        ]:
            if self._set_entry_if_present(attr_name, appearance.get(key_name)):
                applied.append(label)

        set_boolvar(self.axis_text_bold, appearance.get("axis_text_bold"), "axis label bold")
        set_boolvar(self.tick_text_bold, appearance.get("tick_text_bold"), "tick bold")

        for attr_name, key_name, label in [
            ("x_left_margin", "x_left_margin", "left margin"),
            ("x_right_margin", "x_right_margin", "right margin"),
            ("x_margin_bar", "x_margin_bar", "bar X margin"),
            ("y_bottom_margin_linear", "y_bottom_margin_linear", "linear bottom margin"),
            ("y_top_margin_linear", "y_top_margin_linear", "linear top margin"),
            ("y_bottom_margin_log", "y_bottom_margin_log", "log bottom factor"),
            ("y_top_margin_log", "y_top_margin_log", "log top factor"),
        ]:
            if self._set_entry_if_present(attr_name, margins.get(key_name)):
                applied.append(label)

        aliases = settings.get("algorithm_aliases")
        if isinstance(aliases, dict):
            self.algorithm_aliases.update({str(k): str(v) for k, v in aliases.items()})
            applied.append("algorithm names")

        line_styles = settings.get("line_styles")
        if isinstance(line_styles, dict):
            restored = 0
            for key, style_map in line_styles.items():
                if not isinstance(style_map, dict):
                    continue
                marker = style_map.get("marker", "")
                if marker not in MARKER_VALUE_TO_LABEL:
                    marker = ""
                linestyle = style_map.get("linestyle", "solid")
                if isinstance(linestyle, list):
                    linestyle = tuple(linestyle)
                if isinstance(linestyle, str) and linestyle not in LINESTYLE_ID_TO_SPEC:
                    reverse_map = {"-": "solid", "--": "dash", "-.": "dashdot", ":": "dot", "": "none", None: "solid", "None": "none"}
                    linestyle = reverse_map.get(linestyle, "solid")
                elif not isinstance(linestyle, str):
                    linestyle = "solid"
                self.custom_line_styles[str(key)] = {
                    "color": str(style_map.get("color", self.get_algo_line_style(str(key)).get("color", "#4c78a8"))),
                    "marker": marker,
                    "linestyle": linestyle,
                }
                restored += 1
            if restored:
                applied.append(f"line styles ({restored})")

        graph_texts = settings.get("graph_texts")
        if isinstance(graph_texts, dict):
            for lang in ("en", "pt"):
                vals = graph_texts.get(lang, {})
                if isinstance(vals, dict):
                    for key in GRAPH_TEXT_DEFAULTS[lang]:
                        if key in vals:
                            self.custom_graph_texts[lang][key] = str(vals[key])
            applied.append("graph texts")

        component_labels = settings.get("component_labels")
        if isinstance(component_labels, dict):
            for lang in ("en", "pt"):
                vals = component_labels.get(lang, {})
                if isinstance(vals, dict):
                    for key in COMPONENT_LABEL_DEFAULTS[lang]:
                        if key in vals:
                            self.custom_component_labels[lang][key] = str(vals[key])
            applied.append("component labels")

        percent_component_labels = settings.get("percent_component_labels")
        if isinstance(percent_component_labels, dict):
            for lang in ("en", "pt"):
                vals = percent_component_labels.get(lang, {})
                if isinstance(vals, dict):
                    for key in PERCENT_COMPONENT_LABEL_DEFAULTS[lang]:
                        if key in vals:
                            self.custom_percent_component_labels[lang][key] = str(vals[key])
            applied.append("percent component labels")

        component_selection = settings.get("component_selection")
        if isinstance(component_selection, dict):
            normalized = {str(k): bool(v) for k, v in component_selection.items()}
            self.pending_component_selection_from_customizations = normalized
            if self.components_available:
                self.apply_component_selection_from_mapping(normalized)
                applied.append("component selection")
            else:
                skipped.append("component selection (will be applied after loading matching CSV files)")

        summary = []
        if applied:
            summary.append("Applied: " + ", ".join(applied))
        if skipped:
            summary.append("Skipped: " + ", ".join(skipped))
        summary_text = "\n".join(summary) if summary else "No compatible settings were found in this file."
        self.set_status(f"Customizations loaded from {path}")
        messagebox.showinfo("Customizations loaded", summary_text)

    def save_last_plot(self, fmt="svg"):
        if self.last_fig is None:
            messagebox.showerror("Error", "No plot has been generated yet.")
            return

        fmt = str(fmt).strip().lower()
        if fmt not in {"svg", "pdf"}:
            messagebox.showerror("Error", f"Unsupported format: {fmt}")
            return

        ext = f".{fmt}"
        path = filedialog.asksaveasfilename(
            title=f"Save last plot ({fmt.upper()})",
            defaultextension=ext,
            filetypes=[(f"{fmt.upper()} files", f"*{ext}")],
        )
        if not path:
            return
        if os.path.splitext(path)[1].lower() != ext:
            messagebox.showerror("Error", f"Please choose {fmt.upper()} format.")
            return
        try:
            self.last_fig.savefig(path, bbox_inches="tight", format=fmt)
            self.set_status(f"Plot saved to {path}")
        except Exception as exc:
            messagebox.showerror("Error", f"Could not save the plot.\n\n{exc}")

    # ------------------------------
    # Data loading / selection
    # ------------------------------
    def update_plot_mode(self, *args):
        enable_components = self.plot_type.get() == "bar" and bool(self.components_available)
        self.components_button.configure(state="normal" if enable_components else "disabled")

    def cleanup_generated_temp_dir(self):
        if self.generated_temp_dir and os.path.isdir(self.generated_temp_dir):
            try:
                shutil.rmtree(self.generated_temp_dir, ignore_errors=True)
            except Exception:
                pass
        self.generated_temp_dir = None

    def _read_csv_for_type_detection(self, file_path: str):
        read_attempts = [
            {"nrows": 120},
            {"nrows": 120, "engine": "python"},
            {"nrows": 120, "engine": "python", "on_bad_lines": "skip"},
        ]
        for kwargs in read_attempts:
            try:
                return pd.read_csv(file_path, **kwargs)
            except Exception:
                continue
        return None

    def infer_metric_file_type_from_content(self, file_path: str) -> str | None:
        df = self._read_csv_for_type_detection(file_path)
        if df is not None:
            columns_lower = {str(col).strip().lower() for col in df.columns}
            if "metrics" in columns_lower:
                metrics_col = next((col for col in df.columns if str(col).strip().lower() == "metrics"), None)
                metric_values = []
                if metrics_col is not None:
                    try:
                        metric_values = (
                            df[metrics_col]
                            .dropna()
                            .astype(str)
                            .str.strip()
                            .str.lower()
                            .tolist()
                        )
                    except Exception:
                        metric_values = []

                if any("bitrate blocking probability" in value for value in metric_values):
                    return "BitRateBlockingProbability"
                if any(
                    ("blocking probability" in value) and ("bitrate" not in value)
                    for value in metric_values
                ):
                    return "BlockingProbability"
                if any("crosstalk" in value for value in metric_values):
                    return "CrosstalkStatistics"
                if any("modulation" in value for value in metric_values) or {"modulation", "bandwidth"}.issubset(columns_lower):
                    return "ModulationUtilization"
                if any(value == "utilization" for value in metric_values) and {"link", "core", "number of slots", "slot"}.issubset(columns_lower):
                    return "SpectrumUtilization"

            if {"modulation", "bandwidth"}.issubset(columns_lower):
                return "ModulationUtilization"
            if "overlaps" in columns_lower:
                return "CrosstalkStatistics"
            if {"link", "core", "number of slots", "slot"}.issubset(columns_lower):
                return "SpectrumUtilization"

        try:
            with open(file_path, "r", encoding="utf-8", errors="ignore") as fh:
                header = "\n".join([fh.readline() for _ in range(8)]).lower()
        except Exception:
            return None

        if "bitrate blocking probability" in header or "general requested bitrate" in header:
            return "BitRateBlockingProbability"
        if "blocking probability" in header and "bitrate" not in header:
            return "BlockingProbability"
        if "crosstalk" in header or ",overlaps," in header:
            return "CrosstalkStatistics"
        if ",modulation," in header and ",bandwidth," in header:
            return "ModulationUtilization"
        if ",link," in header and ",core," in header and "number of slots" in header and ",slot," in header:
            return "SpectrumUtilization"
        return None

    def detect_metric_file_type(self, file_path: str) -> str | None:
        name = os.path.splitext(os.path.basename(file_path))[0]

        for suffix in sorted(KNOWN_METRIC_FILE_SUFFIXES, key=len, reverse=True):
            if name.endswith("_" + suffix) or name == suffix:
                return suffix

        match = re.search(r"_([A-Za-z][A-Za-z0-9]+)$", name)
        if match:
            guessed = match.group(1)
            if guessed in KNOWN_METRIC_FILE_SUFFIXES:
                return guessed

        return self.infer_metric_file_type_from_content(file_path)

    def get_grouped_metric_files(self, root_folder: str):
        grouped: dict[str, list[str]] = {}
        for current_root, _dirs, files in os.walk(root_folder):
            for file_name in files:
                if not file_name.lower().endswith('.csv'):
                    continue
                full_path = os.path.join(current_root, file_name)
                metric_type = self.detect_metric_file_type(full_path)
                if not metric_type:
                    continue
                grouped.setdefault(metric_type, []).append(full_path)
        for key in grouped:
            grouped[key].sort()
        return grouped

    def _safe_relative_label(self, root_folder: str, file_path: str) -> str:
        parent_dir = os.path.dirname(file_path)
        rel_dir = os.path.relpath(parent_dir, root_folder)
        if rel_dir in {'.', ''}:
            base = os.path.splitext(os.path.basename(file_path))[0]
            metric_type = self.detect_metric_file_type(file_path)
            if metric_type and base.endswith('_' + metric_type):
                return base[:-(len(metric_type) + 1)]
            return base
        return rel_dir.replace('\\', '__').replace('/', '__')

    def _build_merged_csvs_from_folder(self, root_folder: str, selected_types: list[str]):
        grouped_by_folder: dict[str, list[str]] = {}
        for metric_type, files in self.get_grouped_metric_files(root_folder).items():
            if metric_type not in selected_types:
                continue
            for file_path in files:
                label = self._safe_relative_label(root_folder, file_path)
                grouped_by_folder.setdefault(label, []).append(file_path)

        if not grouped_by_folder:
            return [], {}

        self.cleanup_generated_temp_dir()
        temp_dir = tempfile.mkdtemp(prefix='sim_metrics_loader_')
        self.generated_temp_dir = temp_dir

        generated_files = []
        alias_map = {}
        failed_files = []

        for label, files in sorted(grouped_by_folder.items()):
            frames = []
            for file_path in sorted(files):
                df = self.read_csv_safe(file_path)
                if df is None:
                    failed_files.append(file_path)
                    continue
                df = df.copy()
                df['__source_file_type__'] = self.detect_metric_file_type(file_path) or ''
                df['__source_path__'] = file_path
                frames.append(df)
            if not frames:
                continue
            merged_df = pd.concat(frames, ignore_index=True, sort=False)
            safe_name = ''.join(ch if ch.isalnum() or ch in ('-','_') else '_' for ch in label)
            out_path = os.path.join(temp_dir, f'{safe_name}.csv')
            merged_df.to_csv(out_path, index=False)
            generated_files.append(out_path)
            alias_map[os.path.splitext(os.path.basename(out_path))[0]] = label

        if failed_files:
            messagebox.showwarning(
                'Warning',
                'Some files could not be read and were skipped:\n\n' + '\n'.join(failed_files[:20]) + ('\n\n...' if len(failed_files) > 20 else '')
            )

        return generated_files, alias_map

    def finalize_loaded_files(self):
        if not self.csv_files:
            messagebox.showerror('Error', 'No valid CSV files were loaded.')
            return

        current_aliases = dict(self.algorithm_aliases)
        self.algorithm_aliases = {self.get_algo_key(file): current_aliases.get(self.get_algo_key(file), self.get_algo_key(file)) for file in self.csv_files}
        for idx, file in enumerate(self.csv_files):
            algo_key = self.get_algo_key(file)
            default_color = ALGO_STYLES.get(algo_key, {}).get('color', DEFAULT_COLOR_CYCLE[idx % len(DEFAULT_COLOR_CYCLE)])
            if algo_key not in self.custom_line_styles:
                self.custom_line_styles[algo_key] = {
                    'color': default_color,
                    'marker': DEFAULT_MARKER_CYCLE[idx % len(DEFAULT_MARKER_CYCLE)],
                    'linestyle': DEFAULT_LINESTYLE_CYCLE[idx % len(DEFAULT_LINESTYLE_CYCLE)],
                }
            else:
                style = self.custom_line_styles[algo_key]
                if not style.get('color') or (algo_key not in ALGO_STYLES and style.get('color') == '#4c78a8'):
                    style['color'] = DEFAULT_COLOR_CYCLE[idx % len(DEFAULT_COLOR_CYCLE)]
                if not style.get('marker'):
                    style['marker'] = DEFAULT_MARKER_CYCLE[idx % len(DEFAULT_MARKER_CYCLE)]
                if not style.get('linestyle'):
                    style['linestyle'] = DEFAULT_LINESTYLE_CYCLE[idx % len(DEFAULT_LINESTYLE_CYCLE)]
        self.algo_names_button.configure(state='normal')
        if hasattr(self, 'line_styles_button'):
            self.line_styles_button.configure(state='normal')

        metrics_set = set()
        for file in self.csv_files:
            df = self.read_csv_safe(file)
            if df is None or 'Metrics' not in df.columns:
                continue
            metrics_set.update(df['Metrics'].dropna().astype(str).str.strip().unique().tolist())

        self.all_metrics = sorted(metrics_set)
        self.metric_combo.configure(values=self.all_metrics)
        if self.all_metrics:
            preferred_metric = self.pending_metric_from_customizations or self.metric_var.get()
            if preferred_metric in self.all_metrics:
                self.metric_var.set(preferred_metric)
            elif self.metric_var.get() not in self.all_metrics:
                self.metric_var.set(self.all_metrics[0])
        else:
            self.metric_var.set('')
        self.pending_metric_from_customizations = ""
        self.metrics_preview.configure(state='normal')
        self.metrics_preview.delete('1.0', 'end')
        if self.all_metrics:
            self.metrics_preview.insert('1.0', '\n'.join(self.all_metrics))
        else:
            self.metrics_preview.insert('1.0', 'No metrics were found in the loaded CSV files.')
        self.metrics_preview.configure(state='disabled')

        old_selection = self.current_component_selection()
        self.components_available = sorted([m for m in self.all_metrics if is_component_selectable(m)])
        self.component_vars = {}
        for comp in self.components_available:
            self.component_vars[comp] = tk.BooleanVar(value=old_selection.get(comp, True))
        if self.pending_component_selection_from_customizations:
            self.apply_component_selection_from_mapping(self.pending_component_selection_from_customizations)
            self.pending_component_selection_from_customizations = {}

        self.update_components_ui()
        self.update_plot_mode()
        self.update_files_panel()
        self.set_status(f"Loaded {len(self.csv_files)} CSV file(s) and {len(self.all_metrics)} metric(s).")

    def legacy_load_files(self):
        files = filedialog.askopenfilenames(
            title='Select algorithm CSV files',
            filetypes=[('CSV files', '*.csv')],
        )
        if not files:
            return False

        self.cleanup_generated_temp_dir()
        self.csv_files = [f for f in files if os.path.isfile(f)]
        missing = [f for f in files if not os.path.isfile(f)]
        if missing:
            messagebox.showwarning(
                'Warning',
                'The following files could not be found and will be ignored:\n\n' + '\n'.join(missing),
            )
        self.algorithm_aliases = {self.get_algo_key(file): self.get_algo_key(file) for file in self.csv_files}
        self.finalize_loaded_files()
        return bool(self.csv_files)

    def open_load_csv_window(self):
        win = tk.Toplevel(self.root)
        win.title('Load CSV files')
        win.geometry('980x660')
        win.minsize(900, 620)
        win.grab_set()

        folder_var = tk.StringVar()
        info_var = tk.StringVar(value='Select a folder that contains simulation subfolders with result CSV files.')
        available_info_var = tk.StringVar(value='Browse a folder to list the metric file types found in its subfolders.')
        metric_type_items: list[str] = []

        content = ttk.Frame(win, padding=12)
        content.pack(fill='both', expand=True)
        content.columnconfigure(0, weight=1)
        content.rowconfigure(1, weight=1)

        def rebuild_metric_list(grouped):
            metric_listbox.delete(0, tk.END)
            metric_type_items.clear()

            if not grouped:
                available_info_var.set('No metric-type CSV files were found in the selected folder.')
                return

            available_info_var.set(
                'Choose the file types to scan inside the selected folder. '
                'The program will search all subfolders and merge the selected file types per subfolder.'
            )

            for metric_type in sorted(grouped):
                metric_type_items.append(metric_type)
                metric_listbox.insert(tk.END, f"{metric_type} ({len(grouped[metric_type])} file(s))")

            # Keep BlockingProbability preselected when available, otherwise select all.
            selected_any = False
            for idx, metric_type in enumerate(metric_type_items):
                if metric_type == 'BlockingProbability':
                    metric_listbox.selection_set(idx)
                    selected_any = True
            if not selected_any and metric_type_items:
                metric_listbox.selection_set(0, tk.END)

        def browse_folder():
            folder = filedialog.askdirectory(title='Select the parent folder that contains result subfolders')
            if not folder:
                return
            folder_var.set(folder)
            info_var.set('Scanning folder and subfolders for metric files...')
            available_info_var.set('Scanning... please wait.')
            win.update_idletasks()
            grouped = self.get_grouped_metric_files(folder)
            rebuild_metric_list(grouped)
            total_files = sum(len(v) for v in grouped.values())
            if grouped:
                info_var.set(f"Found {total_files} CSV file(s) across {len(grouped)} metric file type(s).")
            else:
                info_var.set('No supported metric-type CSV files were found in the selected folder.')

        def select_all_types():
            if metric_listbox.size() > 0:
                metric_listbox.selection_set(0, tk.END)

        def clear_type_selection():
            metric_listbox.selection_clear(0, tk.END)

        def load_selected_types():
            folder = folder_var.get().strip()
            if not folder or not os.path.isdir(folder):
                messagebox.showerror('Error', 'Select a valid folder first.')
                return

            selected_indices = metric_listbox.curselection()
            selected_types = [metric_type_items[i] for i in selected_indices if 0 <= i < len(metric_type_items)]
            if not selected_types:
                messagebox.showerror('Error', 'Select at least one metric file type.')
                return

            generated_files, alias_map = self._build_merged_csvs_from_folder(folder, selected_types)
            if not generated_files:
                messagebox.showerror('Error', 'No compatible CSV files were found for the selected metric type(s).')
                return
            self.csv_files = generated_files
            self.algorithm_aliases = alias_map
            self.finalize_loaded_files()
            self.set_status(f"Loaded {len(self.csv_files)} merged CSV file(s) from folder mode.")
            win.destroy()

        def load_legacy_from_window():
            if self.legacy_load_files():
                win.destroy()

        top = ttk.LabelFrame(content, text='Folder-based loading', style='Card.TLabelframe')
        top.grid(row=0, column=0, sticky='ew', pady=(0, 8))
        top.columnconfigure(1, weight=1)
        ttk.Button(top, text='Browse folder...', command=browse_folder, style='Primary.TButton').grid(row=0, column=0, padx=(0, 8), pady=6, sticky='w')
        ttk.Entry(top, textvariable=folder_var).grid(row=0, column=1, sticky='ew', pady=6)
        ttk.Label(top, textvariable=info_var, style='Subtitle.TLabel').grid(row=1, column=0, columnspan=2, sticky='w', pady=(0, 4))

        metrics_box = ttk.LabelFrame(content, text='Available metric file types', style='Card.TLabelframe')
        metrics_box.grid(row=1, column=0, sticky='nsew', pady=8)
        metrics_box.columnconfigure(0, weight=1)
        metrics_box.rowconfigure(1, weight=1)

        ttk.Label(metrics_box, textvariable=available_info_var, justify='left', wraplength=860).grid(
            row=0, column=0, sticky='w', pady=(0, 8)
        )

        list_frame = ttk.Frame(metrics_box)
        list_frame.grid(row=1, column=0, sticky='nsew')
        list_frame.columnconfigure(0, weight=1)
        list_frame.rowconfigure(0, weight=1)

        metric_listbox = tk.Listbox(list_frame, selectmode=tk.EXTENDED, exportselection=False, height=8)
        metric_listbox.grid(row=0, column=0, sticky='nsew')
        metric_scroll = ttk.Scrollbar(list_frame, orient='vertical', command=metric_listbox.yview)
        metric_scroll.grid(row=0, column=1, sticky='ns')
        metric_listbox.configure(yscrollcommand=metric_scroll.set)

        list_buttons = ttk.Frame(metrics_box)
        list_buttons.grid(row=2, column=0, sticky='w', pady=(8, 0))
        ttk.Button(list_buttons, text='Select all', command=select_all_types, style='Primary.TButton').pack(side='left')
        ttk.Button(list_buttons, text='Clear selection', command=clear_type_selection, style='Primary.TButton').pack(side='left', padx=(6, 0))

        rebuild_metric_list({})

        legacy_box = ttk.LabelFrame(content, text='Compatibility mode', style='Card.TLabelframe')
        legacy_box.grid(row=2, column=0, sticky='ew', pady=8)
        ttk.Label(legacy_box, text='Use the legacy loader to select CSV files manually, just like in the current version.').pack(anchor='w', pady=(0, 8))
        ttk.Button(legacy_box, text='Load files manually...', command=load_legacy_from_window, style='Primary.TButton').pack(anchor='w')

        buttons = ttk.Frame(content, padding=(0, 8, 0, 0))
        buttons.grid(row=3, column=0, sticky='ew')
        ttk.Button(buttons, text='Cancel', command=win.destroy).pack(side='right')
        ttk.Button(buttons, text='Load selected types', command=load_selected_types, style='Accent.TButton').pack(side='right', padx=(0, 6))

    def load_files(self):
        self.open_load_csv_window()

    def update_components_ui(self):
        count = len(self.components_available)
        self.components_button.configure(text=f"Select components for bar plot ({count})")

    def find_load_index(self, n_points: int, init_load: int, step: int, load_point: int):
        if n_points <= 0:
            return None
        loads = init_load + step * np.arange(n_points)
        return int(np.argmin(np.abs(loads - load_point)))

    # ------------------------------
    # Plot generation dispatcher
    # ------------------------------
    def generate_plot(self):
        active_files = self.get_available_csv_files()
        if not active_files:
            messagebox.showerror("Error", "No valid CSV files are currently available.")
            return
        metric = self.metric_var.get().strip()
        if not metric:
            messagebox.showerror("Error", "Select a metric.")
            return
        try:
            init_load = int(self.init_load.get())
            step = int(self.load_step.get())
            n_rep_user = int(self.n_rep.get())
            axis_font = int(self.axis_font.get())
            tick_font = int(self.tick_font.get())
            legend_font = int(self.legend_font.get())
            x_left_margin = float(self.x_left_margin.get())
            x_right_margin = float(self.x_right_margin.get())
            x_margin_bar = float(self.x_margin_bar.get())
            y_bottom_margin_linear = float(self.y_bottom_margin_linear.get())
            y_top_margin_linear = float(self.y_top_margin_linear.get())
            y_bottom_margin_log = float(self.y_bottom_margin_log.get())
            y_top_margin_log = float(self.y_top_margin_log.get())
            alpha = get_alpha_from_conf(self.conf_level.get())
            n_boot = max(100, int(self.bootstrap_n.get()))
        except ValueError:
            messagebox.showerror("Error", "Please enter valid numeric values in the configuration fields.")
            return

        try:
            selected_loads = self.parse_specific_loads(self.load_filter.get())
        except ValueError as exc:
            messagebox.showerror("Error", str(exc))
            return

        if self.plot_type.get() == "line":
            self.plot_line(
                active_files,
                metric,
                init_load,
                step,
                n_rep_user,
                axis_font,
                tick_font,
                legend_font,
                x_left_margin,
                x_right_margin,
                y_bottom_margin_linear,
                y_top_margin_linear,
                y_bottom_margin_log,
                y_top_margin_log,
                alpha,
                n_boot,
                selected_loads,
            )
            return

        selected_components = [comp for comp, var in self.component_vars.items() if bool(var.get())]
        if not selected_components:
            messagebox.showerror("Error", "Select at least one component for bar plot.")
            return
        try:
            bar_load_point = int(self.bar_load_point.get())
        except ValueError:
            messagebox.showerror("Error", "Please enter a valid load point for bar plot.")
            return

        if self.bar_plot_mode.get() == "percent":
            self.plot_stacked_percent_bars(
                active_files,
                selected_components,
                init_load,
                step,
                n_rep_user,
                bar_load_point,
                axis_font,
                tick_font,
                legend_font,
                x_margin_bar,
            )
        else:
            self.plot_stacked_bars(
                active_files,
                selected_components,
                init_load,
                step,
                n_rep_user,
                bar_load_point,
                axis_font,
                tick_font,
                legend_font,
                x_margin_bar,
                y_bottom_margin_linear,
                y_top_margin_linear,
            )

    # ------------------------------
    # Plot implementations
    # ------------------------------
    def plot_line(
        self,
        files,
        metric,
        init_load,
        step,
        n_rep_user,
        axis_font,
        tick_font,
        legend_font,
        x_left_margin,
        x_right_margin,
        y_bottom_margin_linear,
        y_top_margin_linear,
        y_bottom_margin_log,
        y_top_margin_log,
        alpha,
        n_boot,
        selected_loads=None,
    ):
        fig, ax = plt.subplots(figsize=(10, 7))
        self.last_ci_rows = []
        self.last_best_rows = []
        self.last_ci_metric = metric
        self.last_ci_algorithm = ""
        all_means = []
        all_x_values = []
        max_points = 0
        metric_dfs = {}
        best_candidates = {}

        for file in files:
            df = self.read_csv_safe(file)
            if df is None or "Metrics" not in df.columns:
                continue
            df_m = df[df["Metrics"] == metric].copy().reset_index(drop=True)
            metric_dfs[file] = df_m
            max_points = max(max_points, len(df_m))

        if max_points == 0:
            messagebox.showerror("Error", f"No valid data found for metric: {metric}")
            plt.close(fig)
            return

        global_max_x = (init_load + step * np.arange(max_points))[-1]
        console_series_count = 0

        # Build best-by-load candidates from raw mean series, including zero means.
        # Zero blocking probability is a valid best result and must be considered.
        for file in metric_dfs:
            algo_label = self.get_algo_label(file)
            mean_series_df = self.compute_mean_series(
                file,
                metric,
                init_load,
                step,
                n_rep_user,
                selected_loads=selected_loads,
            )
            if mean_series_df is None or mean_series_df.empty:
                continue
            for row in mean_series_df.itertuples(index=False):
                load_value = int(row.load)
                mean_value = float(row.mean)
                if np.isfinite(mean_value):
                    best_candidates.setdefault(load_value, []).append((algo_label, mean_value))

        for file, df_m in metric_dfs.items():
            algo_key = self.get_algo_key(file)
            algo_label = self.get_algo_label(file)
            style = self.get_algo_line_style(algo_key)
            series = self.compute_series_statistics(
                df_m,
                algo_label,
                init_load,
                step,
                n_rep_user,
                alpha,
                n_boot,
                use_log_plot=bool(self.log_scale.get()),
                selected_loads=selected_loads,
            )
            if series is None:
                continue

            x = series["x"]
            mean = series["mean"]
            lower = series["lower"]
            yerr = series["yerr"]
            truncated = series["truncated"]

            all_x_values.extend(x.tolist())
            all_means.extend(mean.tolist())
            console_series_count += 1
            ci_rows = self.print_ci_table_to_console(metric, algo_label, series)
            self.last_ci_rows.extend(ci_rows)
            self.last_ci_algorithm = self.last_ci_algorithm + (", " if self.last_ci_algorithm else "") + algo_label

            ax.plot(
                x,
                mean,
                color=style.get("color"),
                marker=style.get("marker"),
                linestyle=style.get("linestyle"),
                linewidth=2,
                markersize=8,
                label=algo_label,
            )
            ax.errorbar(
                x,
                mean,
                yerr=yerr,
                color=style.get("color"),
                linewidth=0,
                elinewidth=1.5,
                capsize=4,
                alpha=0.7,
            )

            if self.log_scale.get() and self.get_log_error_mode_key() == "mark" and np.any(truncated):
                mark_y = np.full(np.sum(truncated), self.get_log_plot_epsilon(series["values_valid"]), dtype=float)
                ax.scatter(
                    x[truncated],
                    mark_y,
                    marker="v",
                    s=55,
                    facecolors="white",
                    edgecolors=style.get("color"),
                    linewidths=1.2,
                    zorder=5,
                )

        if not all_means:
            messagebox.showerror("Error", f"No valid data found for metric: {metric}")
            plt.close(fig)
            return

        if self.log_scale.get():
            positive_means = [m for m in all_means if m > 0]
            if not positive_means:
                messagebox.showerror("Error", "Cannot use log scale with all zero values.")
                plt.close(fig)
                return
            ymin_positive = min(positive_means)
            ymax_positive = max(positive_means)
            ymin_power = np.floor(np.log10(ymin_positive))
            ymax_power = np.ceil(np.log10(ymax_positive))
            ymin = 10 ** ymin_power
            ymax = 10 ** ymax_power
            if ymin_positive / ymin < 0.1:
                ymin = 10 ** (ymin_power - 1)
            ax.set_yscale("log")
            ylabel = self.render_graph_text("line_y_log", metric=self.get_metric_label(metric), load="")
            ymin_adjusted = ymin * y_bottom_margin_log
            ymax_adjusted = ymax * y_top_margin_log
        else:
            ymin_mean = min(all_means)
            ymax_mean = max(all_means)
            ymin_adjusted = max(0, ymin_mean - y_bottom_margin_linear)
            ymax_adjusted = ymax_mean + y_top_margin_linear
            ylabel = self.render_graph_text("line_y", metric=self.get_metric_label(metric), load="")

        xmin_real = min(all_x_values)
        xmax_real = max(all_x_values)
        xmin_adjusted = min(init_load, xmin_real - x_left_margin)
        xmax_adjusted = max(global_max_x, xmax_real + x_right_margin)

        print(f"Eixo X - Valores reais: {xmin_real} a {xmax_real}")
        print(f"Margens aplicadas: esquerda={x_left_margin} Erlangs, direita={x_right_margin} Erlangs")

        ax.set_xlim(xmin_adjusted, xmax_adjusted)
        ax.set_ylim(ymin_adjusted, ymax_adjusted)
        ax.set_xticks(sorted(set(all_x_values)))
        ax.tick_params(axis="x", labelsize=tick_font)
        ax.tick_params(axis="y", labelsize=tick_font)
        self.apply_tick_label_weight(ax)
        ax.set_xlabel(self.render_graph_text("line_x", metric=self.get_metric_label(metric), load=""), fontsize=axis_font, fontweight=self.get_axis_label_weight())
        ax.set_ylabel(ylabel, fontsize=axis_font, fontweight=self.get_axis_label_weight())
        title = self.render_graph_text("line_title", metric=self.get_metric_label(metric), load="")
        if title.strip():
            ax.set_title(title, fontsize=axis_font + 2, pad=20)
        if self.last_ci_rows:
            self.export_ci_button.configure(state="normal")
        self.last_best_rows = self.compute_best_by_load_rows(metric, best_candidates)
        self.print_best_by_load_table_to_console(metric, self.last_best_rows)
        self.add_configured_legend(ax, legend_font)
        if self.y_grid_mode.get() == "Major lines only":
            ax.grid(True, which="major", axis="y")
        else:
            ax.grid(True, which="both", axis="y")
        self.finish_plot(fig, status_text=f"Line plot generated for '{metric}'. CI tables printed for {console_series_count} algorithm(s).")

    def plot_stacked_bars(
        self,
        files,
        components,
        init_load,
        step,
        n_rep_user,
        load_point,
        axis_font,
        tick_font,
        legend_font,
        x_margin_bar,
        y_bottom_margin_linear,
        y_top_margin_linear,
    ):
        fig, ax = plt.subplots(figsize=(12, 8))
        algo_names = []
        component_data = {comp: [] for comp in components}

        for file in files:
            algo_name = self.get_algo_label(file)
            algo_names.append(algo_name)
            df = self.read_csv_safe(file)
            if df is None or "Metrics" not in df.columns:
                for comp in components:
                    component_data[comp].append(0.0)
                continue
            for comp in components:
                df_comp = df[df["Metrics"] == comp].copy().reset_index(drop=True)
                load_idx = self.find_load_index(len(df_comp), init_load, step, load_point)
                if len(df_comp) > 0 and load_idx is not None and load_idx < len(df_comp):
                    rep_cols, _ = choose_rep_columns(df_comp, n_rep_user, algo_name)
                    if not rep_cols:
                        component_data[comp].append(0.0)
                        continue
                    df_comp = parse_rep_values(df_comp.copy(), rep_cols)
                    values = df_comp[rep_cols].values
                    mean_value = float(np.nanmean(values[load_idx])) if len(values) > load_idx else 0.0
                    component_data[comp].append(mean_value if np.isfinite(mean_value) else 0.0)
                else:
                    component_data[comp].append(0.0)

        total_data = sum(sum(vals) for vals in component_data.values())
        if total_data == 0:
            messagebox.showerror("Error", f"No data found for the selected components at load point {load_point}.")
            plt.close(fig)
            return

        x_pos = np.arange(len(algo_names))
        width = 0.7
        bottom = np.zeros(len(algo_names))
        legend_elements = []
        from matplotlib.patches import Patch

        for comp in get_component_plot_order(components):
            values = np.asarray(component_data[comp], dtype=float)
            component_keyword = match_component_keyword(comp)
            if component_keyword:
                comp_color = COMPONENT_COLORS.get(component_keyword)
                legend_label = self.get_component_label(component_keyword, comp, percent_mode=False)
            else:
                color_idx = hash(comp) % 10
                comp_color = plt.cm.tab20(color_idx / 10)
                legend_label = comp
            ax.bar(x_pos, values, width, bottom=bottom, color=comp_color, edgecolor="black")
            legend_elements.append(Patch(facecolor=comp_color, edgecolor="black", label=legend_label))
            bottom += values

        metric_label = self.get_metric_label("BlockingProbability")
        ax.set_xlabel(self.render_graph_text("stacked_bar_x", metric=metric_label, load=load_point), fontsize=axis_font, fontweight=self.get_axis_label_weight())
        ax.set_ylabel(self.render_graph_text("stacked_bar_y", metric=metric_label, load=load_point), fontsize=axis_font, fontweight=self.get_axis_label_weight())
        title = self.render_graph_text("stacked_bar_title", metric=metric_label, load=load_point)
        if title.strip():
            ax.set_title(title, fontsize=axis_font + 2, pad=20)
        ax.set_xticks(x_pos)
        ax.set_xticklabels(algo_names, fontsize=tick_font, rotation=45)
        ax.tick_params(axis="y", labelsize=tick_font)
        self.apply_tick_label_weight(ax)
        ax.set_ylim(max(0, -y_bottom_margin_linear), float(np.max(bottom)) + y_top_margin_linear)
        x_margin = x_margin_bar * width
        ax.set_xlim(-x_margin, len(algo_names) - 1 + x_margin)
        if legend_elements:
            self.add_configured_legend(ax, legend_font, handles=legend_elements, count=len(legend_elements))
        ymax = float(np.max(bottom)) + y_top_margin_linear
        for i, total in enumerate(bottom):
            if total > 0:
                ax.text(i, total + (ymax - total) * 0.01, f"{total:.3f}", ha="center", va="bottom", fontsize=max(tick_font - 1, 1))
        ax.grid(True, axis="y", alpha=0.3)
        self.finish_plot(fig, status_text=f"Absolute stacked bar plot generated at load {load_point}.")

    def plot_stacked_percent_bars(
        self,
        files,
        components,
        init_load,
        step,
        n_rep_user,
        load_point,
        axis_font,
        tick_font,
        legend_font,
        x_margin_bar,
    ):
        fig, ax = plt.subplots(figsize=(12, 8))
        algo_names = []
        component_data = {comp: [] for comp in components}

        for file in files:
            algo_name = self.get_algo_label(file)
            algo_names.append(algo_name)
            df = self.read_csv_safe(file)
            if df is None or "Metrics" not in df.columns:
                for comp in components:
                    component_data[comp].append(0.0)
                continue
            for comp in components:
                df_comp = df[df["Metrics"] == comp].copy().reset_index(drop=True)
                load_idx = self.find_load_index(len(df_comp), init_load, step, load_point)
                if len(df_comp) > 0 and load_idx is not None and load_idx < len(df_comp):
                    rep_cols, _ = choose_rep_columns(df_comp, n_rep_user, algo_name)
                    if not rep_cols:
                        component_data[comp].append(0.0)
                        continue
                    df_comp = parse_rep_values(df_comp.copy(), rep_cols)
                    values = df_comp[rep_cols].values
                    mean_value = float(np.nanmean(values[load_idx])) if len(values) > load_idx else 0.0
                    component_data[comp].append(mean_value if np.isfinite(mean_value) else 0.0)
                else:
                    component_data[comp].append(0.0)

        totals = np.zeros(len(algo_names), dtype=float)
        for comp in components:
            totals += np.asarray(component_data[comp], dtype=float)
        if np.allclose(totals, 0.0):
            messagebox.showerror("Error", f"No data found for the selected components at load point {load_point}.")
            plt.close(fig)
            return

        x_pos = np.arange(len(algo_names))
        width = 0.7
        bottom = np.zeros(len(algo_names), dtype=float)
        legend_elements = []
        from matplotlib.patches import Patch

        for comp in get_component_plot_order(components):
            raw_values = np.asarray(component_data[comp], dtype=float)
            values_pct = np.divide(raw_values * 100.0, totals, out=np.zeros_like(raw_values), where=totals > 0)
            component_keyword = match_component_keyword(comp)
            if component_keyword:
                comp_color = COMPONENT_COLORS.get(component_keyword)
                legend_label = self.get_component_label(component_keyword, comp, percent_mode=True)
            else:
                color_idx = hash(comp) % 10
                comp_color = plt.cm.tab20(color_idx / 10)
                legend_label = comp
            ax.bar(x_pos, values_pct, width, bottom=bottom, color=comp_color, edgecolor="white", linewidth=0.4)
            legend_elements.append(Patch(facecolor=comp_color, edgecolor="white", label=legend_label))
            bottom += values_pct

        metric_label = self.get_metric_label("BlockingProbability")
        ax.set_xlabel(self.render_graph_text("percent_bar_x", metric=metric_label, load=load_point), fontsize=axis_font, fontweight=self.get_axis_label_weight())
        ax.set_ylabel(self.render_graph_text("percent_bar_y", metric=metric_label, load=load_point), fontsize=axis_font, fontweight=self.get_axis_label_weight())
        title = self.render_graph_text("percent_bar_title", metric=metric_label, load=load_point)
        if title.strip():
            ax.set_title(title, fontsize=axis_font + 2, pad=20)
        ax.set_xticks(x_pos)
        ax.set_xticklabels(algo_names, fontsize=tick_font, rotation=0)
        ax.set_yticks(np.arange(0, 101, 10))
        ax.set_yticklabels([f"{v}%" for v in range(0, 101, 10)], fontsize=tick_font)
        self.apply_tick_label_weight(ax)
        ax.set_ylim(0, 100)
        x_margin = x_margin_bar * width
        ax.set_xlim(-x_margin, len(algo_names) - 1 + x_margin)
        if legend_elements:
            self.add_configured_legend(ax, legend_font, handles=legend_elements, count=len(legend_elements))
        ax.grid(True, axis="y", alpha=0.3)
        self.finish_plot(fig, status_text=f"100% stacked bar plot generated at load {load_point}.")




# ======================================================
# Headless CLI implementation used by the unified entry point
# ======================================================
import argparse
import importlib.util
import json
import os
import sys
import tempfile
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from scipy.stats import t



# ---------------------------------------------------------------------------
# Self-contained constants and helpers copied/adapted from the GUI analyzer.
# The CLI must not import SimulationDataAnalyzer.py, because the GUI imports
# tkinter and breaks headless/Docker execution when Tk is not installed.
# ---------------------------------------------------------------------------
COMPONENT_KEYWORDS = [
    "lack of transmitters",
    "lack of receivers",
    "fragmentation",
    "QoTN",
    "QoTO",
    "crosstalk in other",
    "crosstalk",
    "other",
]

COMPONENT_LABEL_DEFAULTS = {
    "en": {
        "lack of transmitters": "Transmitters",
        "lack of receivers": "Receivers",
        "fragmentation": "Fragmentation",
        "QoTN": "OSNRN",
        "QoTO": "OSNRO",
        "crosstalk": "XTN",
        "crosstalk in other": "XTO",
        "other": "Other",
    },
    "pt": {
        "lack of transmitters": "Transmissores",
        "lack of receivers": "Receptores",
        "fragmentation": "Fragmentação",
        "QoTN": "OSNRN",
        "QoTO": "OSNRO",
        "crosstalk": "XTN",
        "crosstalk in other": "XTO",
        "other": "Outros",
    },
}

PERCENT_COMPONENT_LABEL_DEFAULTS = {
    "en": {
        "lack of transmitters": "Tx",
        "lack of receivers": "Rx",
        "fragmentation": "Frag",
        "QoTN": "OSNRN",
        "QoTO": "OSNRO",
        "crosstalk": "XTN",
        "crosstalk in other": "XTO",
        "other": "Other",
    },
    "pt": {
        "lack of transmitters": "Tx",
        "lack of receivers": "Rx",
        "fragmentation": "Frag",
        "QoTN": "OSNRN",
        "QoTO": "OSNRO",
        "crosstalk": "XTN",
        "crosstalk in other": "XTO",
        "other": "Outros",
    },
}

ALGO_STYLES = {
    "APAmem": {"color": "#1f77b4", "marker": "o", "linestyle": "solid"},
    "APAnoMem": {"color": "#2ca02c", "marker": "s", "linestyle": "dash"},
    "APAmin": {"color": "#4c78a8", "marker": "^", "linestyle": "dashdot"},
    "CPA": {"color": "#d62728", "marker": "D", "linestyle": "dot"},
    "CPSD": {"color": "#9467bd", "marker": "v", "linestyle": "long_dash"},
    "EnPA": {"color": "#8c564b", "marker": "P", "linestyle": "dense_dashdot"},
    "EPA": {"color": "#e377c2", "marker": "X", "linestyle": "dense_dotted"},
    "IMPA": {"color": "#7f7f7f", "marker": "*", "linestyle": "dash_dot_dot"},
    "PABS": {"color": "#17becf", "marker": "h", "linestyle": "loose_dashdot"},
}

LINESTYLE_OPTIONS = [
    ("solid", "Solid (-)", "-", None),
    ("dash", "Dashed (--)", "--", (8, 4)),
    ("dashdot", "Dash-dot (-.)", "-.", (8, 3, 2, 3)),
    ("dot", "Dotted (:) ", ":", (2, 3)),
    ("long_dash", "Long dash", (0, (12, 4)), (12, 4)),
    ("dense_dashdot", "Dense dash-dot", (0, (6, 2, 2, 2)), (6, 2, 2, 2)),
    ("dense_dotted", "Dense dotted", (0, (1, 2)), (1, 2)),
    ("dash_dot_dot", "Dash-dot-dot", (0, (8, 3, 2, 3, 2, 3)), (8, 3, 2, 3, 2, 3)),
    ("loose_dashdot", "Loose dash-dot", (0, (10, 4, 2, 4)), (10, 4, 2, 4)),
    ("extra_long_dash", "Extra long dash", (0, (16, 5)), (16, 5)),
    ("short_long", "Short-short long", (0, (3, 2, 3, 2, 10, 3)), (3, 2, 3, 2, 10, 3)),
    ("none", "None", "", None),
]
LINESTYLE_ID_TO_SPEC = {style_id: mpl_spec for style_id, _label, mpl_spec, _dash in LINESTYLE_OPTIONS}

DEFAULT_MARKER_CYCLE = ["o", "s", "^", "D", "v", "P", "X", "*", "h", "+", "x"]
DEFAULT_LINESTYLE_CYCLE = [
    "solid",
    "dash",
    "dashdot",
    "dot",
    "long_dash",
    "dense_dashdot",
    "dense_dotted",
    "dash_dot_dot",
    "loose_dashdot",
    "extra_long_dash",
    "short_long",
]
DEFAULT_COLOR_CYCLE = [
    "#1f77b4",
    "#2ca02c",
    "#d62728",
    "#9467bd",
    "#8c564b",
    "#e377c2",
    "#17becf",
    "#ff7f0e",
    "#7f7f7f",
    "#bcbd22",
    "#4c78a8",
]

KNOWN_METRIC_FILE_SUFFIXES = [
    "BitRateBlockingProbability",
    "BlockingProbability",
    "CrosstalkStatistics",
    "ModulationUtilization",
    "SpectrumUtilization",
]

COMPONENT_COLORS = {
    "lack of transmitters": "#7f7f7f",
    "lack of receivers": "#8c564b",
    "fragmentation": "#d62728",
    "QoTN": "#4c72b0",
    "QoTO": "#ed7d31",
    "crosstalk": "#00b050",
    "crosstalk in other": "#ffc000",
    "other": "#e377c2",
}

COMPONENT_PLOT_ORDER = [
    "QoTN",
    "QoTO",
    "crosstalk",
    "crosstalk in other",
    "lack of transmitters",
    "lack of receivers",
    "fragmentation",
    "other",
]

GRAPH_TEXT_DEFAULTS = {
    "en": {
        "line_x": "Network load (Erlangs)",
        "line_y": "{metric}",
        "line_y_log": "{metric} (log10)",
        "line_title": "",
        "stacked_bar_x": "Algorithms",
        "stacked_bar_y": "Blocking probability",
        "stacked_bar_title": "Stacked Components Breakdown at {load} Erlangs",
        "percent_bar_x": "Power assignment algorithms",
        "percent_bar_y": "% of blocking components",
        "percent_bar_title": "Normalized blocking components at {load} Erlangs",
        "simple_bar_x": "Algorithms",
        "simple_bar_y": "{metric}",
        "simple_bar_title": "{metric} at {load} Erlangs",
    },
    "pt": {
        "line_x": "Carga da rede (Erlangs)",
        "line_y": "{metric}",
        "line_y_log": "{metric} (log10)",
        "line_title": "",
        "stacked_bar_x": "Algoritmos",
        "stacked_bar_y": "Probabilidade de bloqueio",
        "stacked_bar_title": "Decomposição empilhada dos componentes em {load} Erlangs",
        "percent_bar_x": "Algoritmos de atribuição de potência",
        "percent_bar_y": "% das componentes de bloqueio",
        "percent_bar_title": "Componentes de bloqueio normalizados em {load} Erlangs",
        "simple_bar_x": "Algoritmos",
        "simple_bar_y": "{metric}",
        "simple_bar_title": "{metric} em {load} Erlangs",
    },
}

METRIC_LABELS = {
    "Blocking probability": {"en": "Blocking probability", "pt": "Probabilidade de bloqueio"},
    "BlockingProbability": {"en": "Blocking probability", "pt": "Probabilidade de bloqueio"},
    "Bit rate blocking probability": {
        "en": "Bit rate blocking probability",
        "pt": "Probabilidade de bloqueio por taxa de bits",
    },
    "BitRateBlockingProbability": {
        "en": "Bit rate blocking probability",
        "pt": "Probabilidade de bloqueio por taxa de bits",
    },
    "BitRate blocking probability": {
        "en": "Bit rate blocking probability",
        "pt": "Probabilidade de bloqueio por taxa de bits",
    },
    "Blocking probability per bitRate": {
        "en": "Bit rate blocking probability",
        "pt": "Probabilidade de bloqueio por taxa de bits",
    },
}

EXCLUDED_COMPONENT_METRICS = {"numm attempts qoto counter (pabs)"}

# Aliases accepted by the CLI. The GUI populates the metric combobox with exact
# values from the CSV "Metrics" column; command-line users often pass metric-file
# names such as BlockingProbability or BitRateBlockingProbability instead.
METRIC_ALIASES = {
    "BlockingProbability": [
        "Blocking probability",
    ],
    "BitRateBlockingProbability": [
        "Blocking probability per bitRate",
        "Bit rate blocking probability",
        "BitRate blocking probability",
        "BitRateBlockingProbability",
    ],
    "CrosstalkStatistics": [
        "Crosstalk statistics",
    ],
    "ModulationUtilization": [
        "Modulation utilization",
    ],
    "SpectrumUtilization": [
        "Spectrum utilization",
    ],
}


def get_alpha_from_conf(conf_str: str) -> float:
    return {"90%": 0.10, "95%": 0.05, "99%": 0.01}.get(str(conf_str), 0.05)


def bootstrap_ci_mean(row_1d, alpha=0.05, n_boot=2000, rng=None):
    x = np.asarray(row_1d, dtype=float)
    x = x[~np.isnan(x)]
    if x.size < 2:
        m = float(np.nanmean(x)) if x.size else 0.0
        return m, m

    if rng is None:
        rng = np.random.default_rng()

    n = x.size
    samples = rng.choice(x, size=(n_boot, n), replace=True)
    boot_means = samples.mean(axis=1)
    lower = np.percentile(boot_means, 100 * (alpha / 2))
    upper = np.percentile(boot_means, 100 * (1 - alpha / 2))
    return float(lower), float(upper)


def detect_rep_columns(df: pd.DataFrame):
    rep_cols = [c for c in df.columns if str(c).lower().startswith("rep")]

    def rep_key(col):
        s = str(col).lower()
        suf = s[3:]
        try:
            return (0, int(suf))
        except Exception:
            return (1, s)

    rep_cols.sort(key=rep_key)
    return rep_cols


def choose_rep_columns(df: pd.DataFrame, n_rep_user: int, algo_name_for_msg: str):
    rep_cols_all = detect_rep_columns(df)
    if not rep_cols_all:
        return [], 0

    n_avail = len(rep_cols_all)
    if n_rep_user <= 0:
        return rep_cols_all, n_avail

    if n_rep_user > n_avail:
        print(
            f"Warning: {algo_name_for_msg}: CSV has only {n_avail} replications, "
            f"but {n_rep_user} were requested. Using {n_avail}.",
            file=sys.stderr,
        )
        return rep_cols_all, n_avail

    if n_rep_user < n_avail:
        print(
            f"Warning: {algo_name_for_msg}: CSV has {n_avail} replications, "
            f"but {n_rep_user} were requested. Using the first {n_rep_user}.",
            file=sys.stderr,
        )
        return rep_cols_all[:n_rep_user], n_rep_user

    return rep_cols_all, n_avail


def parse_rep_values(df: pd.DataFrame, rep_cols):
    for c in rep_cols:
        df[c] = pd.to_numeric(df[c].astype(str).str.replace(",", ".", regex=False), errors="coerce")
    return df


def match_component_keyword(metric: str):
    metric_l = str(metric).lower()
    for kw in sorted(COMPONENT_KEYWORDS, key=len, reverse=True):
        if kw.lower() in metric_l:
            return kw
    return None


def is_component_selectable(metric: str) -> bool:
    if match_component_keyword(metric) is None:
        return False
    return str(metric).strip().lower() not in EXCLUDED_COMPONENT_METRICS


def get_component_plot_order(components):
    priority = {name: idx for idx, name in enumerate(COMPONENT_PLOT_ORDER)}

    def sort_key(comp):
        keyword = match_component_keyword(comp)
        if keyword in priority:
            return (0, priority[keyword], str(comp).lower())
        return (1, 999, str(comp).lower())

    return sorted(components, key=sort_key)


def normalize_metric_name(value: str) -> str:
    return "".join(ch for ch in str(value).lower() if ch.isalnum())


def resolve_metric_name(requested: str | None, discovered_metrics: list[str]) -> str | None:
    """Resolve CLI metric aliases to the exact value used in the CSV Metrics column."""
    if not discovered_metrics:
        return None
    if requested is None or str(requested).strip() == "":
        return discovered_metrics[0]

    requested = str(requested).strip()

    # 1) Exact match, matching the GUI combobox behavior.
    if requested in discovered_metrics:
        return requested

    # 2) Case-insensitive exact match.
    lower_map = {m.lower(): m for m in discovered_metrics}
    if requested.lower() in lower_map:
        return lower_map[requested.lower()]

    # 3) Normalized match: BlockingProbability <-> Blocking probability.
    normalized_map = {normalize_metric_name(m): m for m in discovered_metrics}
    norm_requested = normalize_metric_name(requested)
    if norm_requested in normalized_map:
        return normalized_map[norm_requested]

    # 4) Known semantic aliases.
    alias_candidates = METRIC_ALIASES.get(requested, [])
    alias_candidates += METRIC_ALIASES.get(norm_requested, [])
    for candidate in alias_candidates:
        if candidate in discovered_metrics:
            return candidate
        norm_candidate = normalize_metric_name(candidate)
        if norm_candidate in normalized_map:
            return normalized_map[norm_candidate]

    # 5) Special mappings for common file-type names.
    if norm_requested == "bitrateblockingprobability":
        for candidate in discovered_metrics:
            normalized = normalize_metric_name(candidate)
            if "bitrate" in normalized and "blockingprobability" in normalized:
                return candidate
    if norm_requested == "blockingprobability":
        for candidate in discovered_metrics:
            normalized = normalize_metric_name(candidate)
            if normalized == "blockingprobability":
                return candidate
        for candidate in discovered_metrics:
            normalized = normalize_metric_name(candidate)
            if "blockingprobability" in normalized and "bitrate" not in normalized:
                return candidate

    return None


def resolve_component_names(requested_components: list[str], discovered_metrics: list[str]) -> list[str]:
    """Resolve component aliases in --components to exact metric names when possible."""
    resolved = []
    for comp in requested_components:
        exact = resolve_metric_name(comp, discovered_metrics)
        if exact:
            resolved.append(exact)
            continue
        # Keep component keywords such as "QoTN" or "crosstalk" by mapping to the
        # first matching metric if the exact metric name was not supplied.
        comp_l = str(comp).lower()
        match = next((m for m in discovered_metrics if comp_l in str(m).lower()), None)
        resolved.append(match or comp)
    return resolved



def load_json_object(path_str: str | None) -> dict:
    if not path_str:
        return {}
    path = Path(path_str)
    if not path.exists():
        raise FileNotFoundError(f"JSON file not found: {path}")
    with path.open('r', encoding='utf-8') as fh:
        data = json.load(fh)
    if not isinstance(data, dict):
        raise ValueError(f"Expected a JSON object in: {path}")
    return data


def normalize_gui_settings_payload(payload: dict) -> dict:
    settings = payload.get('settings') if isinstance(payload, dict) and isinstance(payload.get('settings'), dict) else None
    if settings is not None:
        return settings
    return {
        'general': {
            'theme_name': payload.get('theme_name'),
            'graph_language': payload.get('graph_language'),
            'plot_type': payload.get('plot_type'),
            'log_scale': payload.get('log_scale'),
            'metric': payload.get('metric'),
        },
        'plot': {
            'bar_plot_mode': payload.get('bar_plot_mode'),
            'log_error_mode': payload.get('log_error_mode'),
            'y_grid_mode': payload.get('y_grid_mode'),
            'legend_position': payload.get('legend_position'),
        },
        'statistics': {
            'confidence_level': payload.get('conf_level') or payload.get('confidence_level'),
            'ci_method': payload.get('ci_method'),
            'bootstrap_resamples': payload.get('bootstrap_n'),
        },
        'loads': {
            'init_load': payload.get('init_load'),
            'load_step': payload.get('load_step'),
            'replications': payload.get('n_rep') or payload.get('replications'),
            'bar_load_point': payload.get('bar_load_point'),
            'load_filter': payload.get('load_filter'),
        },
        'appearance': {
            'axis_font': payload.get('axis_font'),
            'tick_font': payload.get('tick_font'),
            'legend_font': payload.get('legend_font'),
            'axis_text_bold': payload.get('axis_text_bold'),
            'tick_text_bold': payload.get('tick_text_bold'),
        },
        'margins': {
            'x_left_margin': payload.get('x_left_margin'),
            'x_right_margin': payload.get('x_right_margin'),
            'x_margin_bar': payload.get('x_margin_bar'),
            'y_bottom_margin_linear': payload.get('y_bottom_margin_linear'),
            'y_top_margin_linear': payload.get('y_top_margin_linear'),
            'y_bottom_margin_log': payload.get('y_bottom_margin_log'),
            'y_top_margin_log': payload.get('y_top_margin_log'),
        },
        'algorithm_aliases': payload.get('algorithm_aliases'),
        'line_styles': payload.get('line_styles'),
        'graph_texts': payload.get('graph_texts'),
        'component_labels': payload.get('component_labels'),
        'percent_component_labels': payload.get('percent_component_labels'),
        'component_selection': payload.get('component_selection'),
    }


def _coerce_int(value):
    if value is None or value == '':
        return None
    return int(value)


def _coerce_float(value):
    if value is None or value == '':
        return None
    return float(value)


def _coerce_bool(value):
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)) and value in {0, 1}:
        return bool(value)
    if isinstance(value, str):
        v = value.strip().lower()
        if v in {'true', '1', 'yes', 'y', 'on'}:
            return True
        if v in {'false', '0', 'no', 'n', 'off'}:
            return False
    return None


def _collect_provided_options(argv: list[str]) -> set[str]:
    provided = set()
    for token in argv:
        if token.startswith('--'):
            provided.add(token.split('=', 1)[0])
    return provided


def normalize_cli_compact_config_payload(payload: dict) -> dict:
    if not isinstance(payload, dict):
        raise ValueError("Compact CLI config must be a JSON object.")

    if isinstance(payload.get('settings'), dict):
        payload = dict(payload)

    general = payload.get('general', {}) if isinstance(payload.get('general'), dict) else {}
    plot = payload.get('plot', {}) if isinstance(payload.get('plot'), dict) else {}
    statistics = payload.get('statistics', {}) if isinstance(payload.get('statistics'), dict) else {}
    loads = payload.get('loads', {}) if isinstance(payload.get('loads'), dict) else {}
    appearance = payload.get('appearance', {}) if isinstance(payload.get('appearance'), dict) else {}
    margins = payload.get('margins', {}) if isinstance(payload.get('margins'), dict) else {}
    io_cfg = payload.get('io', {}) if isinstance(payload.get('io'), dict) else {}

    compact = {
        'io': {
            'input': io_cfg.get('input', payload.get('input')),
            'output': io_cfg.get('output', payload.get('output')),
            'ci_output': io_cfg.get('ci_output', payload.get('ci_output')),
            'best_output': io_cfg.get('best_output', payload.get('best_output')),
        },
        'general': {
            'graph_language': general.get('graph_language', payload.get('language')),
            'plot_type': general.get('plot_type', payload.get('plot')),
            'log_scale': general.get('log_scale', payload.get('log_scale')),
            'metric': general.get('metric', payload.get('metric')),
            'theme_name': general.get('theme_name', payload.get('theme_name')),
        },
        'plot': {
            'metric_types': plot.get('metric_types', plot.get('metric_type', payload.get('metric_type', payload.get('metric_types')))),
            'bar_plot_mode': plot.get('bar_plot_mode', plot.get('bar_mode', payload.get('bar_mode'))),
            'components': plot.get('components', payload.get('components')),
            'log_error_mode': plot.get('log_error_mode', payload.get('log_error_mode')),
            'y_grid_mode': plot.get('y_grid_mode', payload.get('y_grid_mode')),
            'legend_position': plot.get('legend_position', payload.get('legend_position')),
        },
        'statistics': {
            'confidence_level': statistics.get('confidence_level', statistics.get('confidence', payload.get('confidence'))),
            'ci_method': statistics.get('ci_method', payload.get('ci_method')),
            'bootstrap_resamples': statistics.get('bootstrap_resamples', statistics.get('bootstrap', payload.get('bootstrap'))),
        },
        'loads': {
            'init_load': loads.get('init_load', payload.get('init_load')),
            'load_step': loads.get('load_step', payload.get('load_step')),
            'replications': loads.get('replications', payload.get('replications')),
            'bar_load_point': loads.get('bar_load_point', loads.get('load_point', payload.get('load_point'))),
            'load_filter': loads.get('load_filter', payload.get('load_filter')),
        },
        'appearance': {
            'axis_font': appearance.get('axis_font', payload.get('axis_font')),
            'tick_font': appearance.get('tick_font', payload.get('tick_font')),
            'legend_font': appearance.get('legend_font', payload.get('legend_font')),
            'axis_text_bold': appearance.get('axis_text_bold', payload.get('axis_text_bold')),
            'tick_text_bold': appearance.get('tick_text_bold', payload.get('tick_text_bold')),
        },
        'margins': {
            'x_left_margin': margins.get('x_left_margin', payload.get('x_left_margin')),
            'x_right_margin': margins.get('x_right_margin', payload.get('x_right_margin')),
            'x_margin_bar': margins.get('x_margin_bar', payload.get('x_margin_bar')),
            'y_bottom_margin_linear': margins.get('y_bottom_margin_linear', payload.get('y_bottom_margin_linear')),
            'y_top_margin_linear': margins.get('y_top_margin_linear', payload.get('y_top_margin_linear')),
            'y_bottom_margin_log': margins.get('y_bottom_margin_log', payload.get('y_bottom_margin_log')),
            'y_top_margin_log': margins.get('y_top_margin_log', payload.get('y_top_margin_log')),
        },
        'algorithm_aliases': payload.get('algorithm_aliases', payload.get('aliases')),
        'line_styles': payload.get('line_styles'),
        'graph_texts': payload.get('graph_texts'),
        'component_labels': payload.get('component_labels'),
        'percent_component_labels': payload.get('percent_component_labels'),
        'component_selection': payload.get('component_selection'),
    }
    return compact


def _normalize_metric_types(raw_value) -> list[str] | None:
    if raw_value is None or raw_value == '':
        return None
    if isinstance(raw_value, str):
        parts = [part.strip() for part in raw_value.split(',') if part.strip()]
        return parts or None
    if isinstance(raw_value, (list, tuple)):
        parts = [str(part).strip() for part in raw_value if str(part).strip()]
        return parts or None
    return [str(raw_value).strip()] if str(raw_value).strip() else None


def _normalize_components(raw_value) -> str | None:
    if raw_value is None or raw_value == '':
        return None
    if isinstance(raw_value, str):
        return raw_value
    if isinstance(raw_value, (list, tuple)):
        parts = [str(part).strip() for part in raw_value if str(part).strip()]
        return ','.join(parts) if parts else None
    return str(raw_value)


def apply_canonical_config_to_args(
    args: argparse.Namespace,
    argv: list[str],
    canonical: dict,
    *,
    source_label: str = 'config',
) -> argparse.Namespace:
    provided = _collect_provided_options(argv)

    general = canonical.get('general', {}) if isinstance(canonical.get('general'), dict) else {}
    plot = canonical.get('plot', {}) if isinstance(canonical.get('plot'), dict) else {}
    statistics = canonical.get('statistics', {}) if isinstance(canonical.get('statistics'), dict) else {}
    loads = canonical.get('loads', {}) if isinstance(canonical.get('loads'), dict) else {}
    appearance = canonical.get('appearance', {}) if isinstance(canonical.get('appearance'), dict) else {}
    margins = canonical.get('margins', {}) if isinstance(canonical.get('margins'), dict) else {}
    io_cfg = canonical.get('io', {}) if isinstance(canonical.get('io'), dict) else {}

    mappings = [
        ('--input', 'input', io_cfg.get('input'), str),
        ('--output', 'output', io_cfg.get('output'), str),
        ('--ci-output', 'ci_output', io_cfg.get('ci_output'), str),
        ('--best-output', 'best_output', io_cfg.get('best_output'), str),
        ('--language', 'language', general.get('graph_language'), str),
        ('--plot', 'plot', general.get('plot_type'), str),
        ('--metric', 'metric', general.get('metric'), str),
        ('--bar-mode', 'bar_mode', plot.get('bar_plot_mode'), str),
        ('--log-error-mode', 'log_error_mode', plot.get('log_error_mode'), str),
        ('--y-grid-mode', 'y_grid_mode', plot.get('y_grid_mode'), str),
        ('--legend-position', 'legend_position', plot.get('legend_position'), str),
        ('--confidence', 'confidence', statistics.get('confidence_level'), str),
        ('--ci-method', 'ci_method', statistics.get('ci_method'), str),
        ('--bootstrap', 'bootstrap', statistics.get('bootstrap_resamples'), _coerce_int),
        ('--init-load', 'init_load', loads.get('init_load'), _coerce_int),
        ('--load-step', 'load_step', loads.get('load_step'), _coerce_int),
        ('--replications', 'replications', loads.get('replications'), _coerce_int),
        ('--load-point', 'load_point', loads.get('bar_load_point'), _coerce_int),
        ('--load-filter', 'load_filter', loads.get('load_filter'), str),
        ('--axis-font', 'axis_font', appearance.get('axis_font'), _coerce_int),
        ('--tick-font', 'tick_font', appearance.get('tick_font'), _coerce_int),
        ('--legend-font', 'legend_font', appearance.get('legend_font'), _coerce_int),
        ('--x-left-margin', 'x_left_margin', margins.get('x_left_margin'), _coerce_float),
        ('--x-right-margin', 'x_right_margin', margins.get('x_right_margin'), _coerce_float),
        ('--x-margin-bar', 'x_margin_bar', margins.get('x_margin_bar'), _coerce_float),
        ('--y-bottom-margin-linear', 'y_bottom_margin_linear', margins.get('y_bottom_margin_linear'), _coerce_float),
        ('--y-top-margin-linear', 'y_top_margin_linear', margins.get('y_top_margin_linear'), _coerce_float),
        ('--y-bottom-margin-log', 'y_bottom_margin_log', margins.get('y_bottom_margin_log'), _coerce_float),
        ('--y-top-margin-log', 'y_top_margin_log', margins.get('y_top_margin_log'), _coerce_float),
    ]

    for opt, attr, raw_value, caster in mappings:
        if opt in provided or raw_value is None:
            continue
        try:
            value = caster(raw_value) if caster is not str else str(raw_value)
        except Exception:
            continue
        if value is not None:
            setattr(args, attr, value)

    metric_types = _normalize_metric_types(plot.get('metric_types'))
    if '--metric-type' not in provided and metric_types is not None:
        args.metric_type = metric_types

    components_value = _normalize_components(plot.get('components'))
    if '--components' not in provided and components_value is not None:
        args.components = components_value

    bool_mappings = [
        ('--log-scale', 'log_scale', general.get('log_scale')),
        ('--axis-text-bold', 'axis_text_bold', appearance.get('axis_text_bold')),
        ('--tick-text-bold', 'tick_text_bold', appearance.get('tick_text_bold')),
    ]
    for opt, attr, raw_value in bool_mappings:
        if opt in provided:
            continue
        value = _coerce_bool(raw_value)
        if value is not None:
            setattr(args, attr, value)

    component_selection = canonical.get('component_selection')
    if '--components' not in provided and isinstance(component_selection, dict):
        selected = [str(k) for k, v in component_selection.items() if bool(v)]
        if selected:
            args.components = ','.join(selected)

    return args


def apply_settings_json_to_args(args: argparse.Namespace, argv: list[str]) -> argparse.Namespace:
    if not getattr(args, 'settings_json', None):
        return args

    payload = load_json_object(args.settings_json)
    settings = normalize_gui_settings_payload(payload)
    return apply_canonical_config_to_args(args, argv, settings, source_label='settings-json')


def apply_compact_config_to_args(args: argparse.Namespace, argv: list[str]) -> argparse.Namespace:
    if not getattr(args, 'config', None):
        return args

    payload = load_json_object(args.config)
    canonical = normalize_cli_compact_config_payload(payload)
    return apply_canonical_config_to_args(args, argv, canonical, source_label='config')


class HeadlessAnalyzer:
    """Headless adapter that reuses the same CSV conventions used by the GUI."""

    def __init__(self, args: argparse.Namespace):
        self.args = args
        self.generated_temp_dir: str | None = None

        settings_payload = load_json_object(args.settings_json) if getattr(args, 'settings_json', None) else {}
        gui_settings = normalize_gui_settings_payload(settings_payload) if settings_payload else {}

        compact_payload = load_json_object(args.config) if getattr(args, 'config', None) else {}
        compact_settings = normalize_cli_compact_config_payload(compact_payload) if compact_payload else {}

        self.settings_sections = self._merge_nested_defaults(gui_settings, compact_settings)

        settings_aliases = self.settings_sections.get('algorithm_aliases') if isinstance(self.settings_sections.get('algorithm_aliases'), dict) else {}
        explicit_aliases = load_json_object(args.aliases_json) if args.aliases_json else {}
        self.algorithm_aliases: dict[str, str] = {str(k): str(v) for k, v in {**settings_aliases, **explicit_aliases}.items()}

        settings_graph_texts = self.settings_sections.get('graph_texts') if isinstance(self.settings_sections.get('graph_texts'), dict) else {}
        explicit_graph_texts = load_json_object(args.graph_texts_json) if args.graph_texts_json else {}
        self.custom_graph_texts: dict[str, dict[str, str]] = self._merge_nested_defaults(
            GRAPH_TEXT_DEFAULTS,
            self._merge_nested_defaults(settings_graph_texts, explicit_graph_texts),
        )

        settings_component_labels = self.settings_sections.get('component_labels') if isinstance(self.settings_sections.get('component_labels'), dict) else {}
        explicit_component_labels = load_json_object(args.component_labels_json) if args.component_labels_json else {}
        self.custom_component_labels: dict[str, dict[str, str]] = self._merge_nested_defaults(
            COMPONENT_LABEL_DEFAULTS,
            self._merge_nested_defaults(settings_component_labels, explicit_component_labels),
        )

        settings_percent_labels = self.settings_sections.get('percent_component_labels') if isinstance(self.settings_sections.get('percent_component_labels'), dict) else {}
        explicit_percent_labels = load_json_object(args.percent_component_labels_json) if args.percent_component_labels_json else {}
        self.custom_percent_component_labels: dict[str, dict[str, str]] = self._merge_nested_defaults(
            PERCENT_COMPONENT_LABEL_DEFAULTS,
            self._merge_nested_defaults(settings_percent_labels, explicit_percent_labels),
        )

        settings_line_styles = self.settings_sections.get('line_styles') if isinstance(self.settings_sections.get('line_styles'), dict) else {}
        explicit_line_styles = load_json_object(args.line_styles_json) if args.line_styles_json else {}
        self.custom_line_styles: dict[str, dict[str, Any]] = {**settings_line_styles, **explicit_line_styles}
        self.component_selection_from_settings: dict[str, bool] = {
            str(k): bool(v) for k, v in (self.settings_sections.get('component_selection') or {}).items()
        } if isinstance(self.settings_sections.get('component_selection'), dict) else {}

    # ------------------------------------------------------------------
    # Generic utilities
    # ------------------------------------------------------------------
    def _merge_nested_defaults(self, defaults: dict, overrides: dict) -> dict:
        merged = json.loads(json.dumps(defaults))
        for key, value in overrides.items():
            if isinstance(value, dict) and isinstance(merged.get(key), dict):
                merged[key].update(value)
            else:
                merged[key] = value
        return merged

    def cleanup_generated_temp_dir(self):
        if self.generated_temp_dir and os.path.isdir(self.generated_temp_dir):
            import shutil
            shutil.rmtree(self.generated_temp_dir, ignore_errors=True)
        self.generated_temp_dir = None

    def read_csv_safe(self, file_path: str):
        try:
            return pd.read_csv(file_path)
        except FileNotFoundError:
            return None
        except pd.errors.ParserError:
            try:
                return pd.read_csv(file_path, engine="python")
            except Exception as exc:
                print(f"Warning: could not parse file {file_path}: {exc}", file=sys.stderr)
                return None
        except Exception as exc:
            print(f"Warning: could not read file {file_path}: {exc}", file=sys.stderr)
            return None

    def _read_csv_for_type_detection(self, file_path: str):
        read_attempts = [
            {"nrows": 120},
            {"nrows": 120, "engine": "python"},
            {"nrows": 120, "engine": "python", "on_bad_lines": "skip"},
        ]
        for kwargs in read_attempts:
            try:
                return pd.read_csv(file_path, **kwargs)
            except Exception:
                continue
        return None

    def infer_metric_file_type_from_content(self, file_path: str) -> str | None:
        df = self._read_csv_for_type_detection(file_path)
        if df is not None:
            columns_lower = {str(col).strip().lower() for col in df.columns}
            if "metrics" in columns_lower:
                metrics_col = next((col for col in df.columns if str(col).strip().lower() == "metrics"), None)
                metric_values = []
                if metrics_col is not None:
                    try:
                        metric_values = (
                            df[metrics_col].dropna().astype(str).str.strip().str.lower().tolist()
                        )
                    except Exception:
                        metric_values = []

                if any("bitrate blocking probability" in value for value in metric_values):
                    return "BitRateBlockingProbability"
                if any(("blocking probability" in value) and ("bitrate" not in value) for value in metric_values):
                    return "BlockingProbability"
                if any("crosstalk" in value for value in metric_values):
                    return "CrosstalkStatistics"
                if any("modulation" in value for value in metric_values):
                    return "ModulationUtilization"
                if any("slot" in value or "spectrum" in value for value in metric_values):
                    return "SpectrumUtilization"

        try:
            with open(file_path, "r", encoding="utf-8", errors="ignore") as fh:
                header = fh.read(4096).lower()
        except Exception:
            return None

        if "bitrate blocking probability" in header or "general requested bitrate" in header:
            return "BitRateBlockingProbability"
        if "blocking probability" in header and "bitrate" not in header:
            return "BlockingProbability"
        if "crosstalk" in header or ",overlaps," in header:
            return "CrosstalkStatistics"
        if ",modulation," in header and ",bandwidth," in header:
            return "ModulationUtilization"
        if ",link," in header and ",core," in header and "number of slots" in header and ",slot," in header:
            return "SpectrumUtilization"
        return None

    def detect_metric_file_type(self, file_path: str) -> str | None:
        path = Path(file_path)
        name = os.path.splitext(os.path.basename(file_path))[0]
        normalized_parts = {self._normalize_name_for_matching(part) for part in path.parts}

        for suffix in sorted(KNOWN_METRIC_FILE_SUFFIXES, key=len, reverse=True):
            if name.endswith("_" + suffix) or name == suffix:
                return suffix

        # Important for repository layouts like:
        #   USA/BitRateBlockingProbability/IMPA.csv
        # where the file itself is named by algorithm and the metric type is
        # encoded in the parent folder. Without this, PBBR plots can be mixed
        # with regular BlockingProbability data.
        for suffix in sorted(KNOWN_METRIC_FILE_SUFFIXES, key=len, reverse=True):
            if self._normalize_name_for_matching(suffix) in normalized_parts:
                return suffix

        return self.infer_metric_file_type_from_content(file_path)

    def get_grouped_metric_files(self, root_folder: str):
        grouped: dict[str, list[str]] = {}
        for current_root, _dirs, files in os.walk(root_folder):
            for file_name in files:
                if not file_name.lower().endswith(".csv"):
                    continue
                full_path = os.path.join(current_root, file_name)
                metric_type = self.detect_metric_file_type(full_path)
                if not metric_type:
                    continue
                grouped.setdefault(metric_type, []).append(full_path)
        for key in grouped:
            grouped[key].sort()
        return grouped

    def _normalize_name_for_matching(self, value: str) -> str:
        return "".join(ch for ch in str(value).lower() if ch.isalnum())

    def _strip_metric_suffix_from_stem(self, stem: str, metric_type: str | None) -> str | None:
        """Return the algorithm prefix when the file name is Algo_MetricType."""
        if not metric_type:
            return None
        suffix = f"_{metric_type}"
        if stem.endswith(suffix) and len(stem) > len(suffix):
            return stem[: -len(suffix)]

        # Be permissive with case and separators, e.g. IMPA-blockingprobability.
        norm_stem = self._normalize_name_for_matching(stem)
        norm_metric = self._normalize_name_for_matching(metric_type)
        if norm_stem.endswith(norm_metric) and norm_stem != norm_metric:
            # Prefer a readable split on common separators.
            for sep in ("_", "-", "."):
                token = sep + metric_type
                if stem.lower().endswith(token.lower()):
                    candidate = stem[: -len(token)]
                    if candidate.strip():
                        return candidate
        return None

    def _infer_algorithm_label_from_path(self, root_folder: str, file_path: str, metric_type: str | None) -> str:
        """Infer the algorithm/series label for a CSV file.

        The GUI plots one line/bar per loaded CSV file and uses the CSV file name
        as the algorithm key. The folder loader introduced a second layout where
        several metric files are merged into one temporary CSV per algorithm.

        This method supports the common repository layouts without turning metric
        names into algorithm names:

        1. USA/IMPA/BlockingProbability.csv              -> IMPA
        2. USA/BlockingProbability/IMPA.csv              -> IMPA
        3. USA/IMPA_BlockingProbability.csv              -> IMPA
        4. USA/IMPA.csv                                  -> IMPA
        5. USA/Topology/IMPA/BlockingProbability.csv     -> Topology__IMPA

        The previous CLI used the relative parent directory unconditionally. In
        layout 2, that made the algorithm label become "BlockingProbability".
        """
        root = Path(root_folder).resolve()
        path = Path(file_path).resolve()
        stem = path.stem

        known_metric_norms = {self._normalize_name_for_matching(x) for x in KNOWN_METRIC_FILE_SUFFIXES}
        if metric_type:
            known_metric_norms.add(self._normalize_name_for_matching(metric_type))

        # File name like IMPA_BlockingProbability.csv.
        prefix = self._strip_metric_suffix_from_stem(stem, metric_type)
        if prefix:
            return prefix

        stem_norm = self._normalize_name_for_matching(stem)
        try:
            rel_parts = list(path.relative_to(root).parts)
        except Exception:
            rel_parts = list(path.parts)
        parent_parts = rel_parts[:-1]
        parent_norms = [self._normalize_name_for_matching(part) for part in parent_parts]

        # If the file name itself is not a metric type, it is usually the
        # algorithm name, especially in MetricType/Algorithm.csv layouts.
        if stem_norm not in known_metric_norms:
            return stem

        # File name is a metric type, e.g. IMPA/BlockingProbability.csv.
        # Use the nearest non-metric parent path as the algorithm label.
        non_metric_parent_parts = [
            part for part, norm in zip(parent_parts, parent_norms)
            if norm and norm not in known_metric_norms and part not in {".", ""}
        ]
        if non_metric_parent_parts:
            return "__".join(non_metric_parent_parts)

        # Last-resort fallback: keep the stem. This means the folder has metric
        # files at the root and no algorithm information can be inferred from the
        # path. The caller will emit a warning for this case.
        return stem

    def _safe_output_stem(self, label: str) -> str:
        safe = "".join(ch if ch.isalnum() or ch in ("-", "_") else "_" for ch in str(label))
        safe = safe.strip("_")
        return safe or "series"

    def build_merged_csvs_from_folder(self, root_folder: str, selected_types: list[str]):
        """Build one temporary merged CSV per algorithm/series.

        This mirrors the GUI behavior, but fixes the CLI-specific bug where
        metric folder names were accidentally used as algorithm names. Each output
        temporary CSV represents an algorithm/series and may contain rows from one
        or more metric files.
        """
        selected_set = set(selected_types or [])
        grouped_by_algorithm: dict[str, list[str]] = {}
        metric_like_labels: set[str] = set()

        grouped_metrics = self.get_grouped_metric_files(root_folder)
        for metric_type, files in grouped_metrics.items():
            if selected_set and metric_type not in selected_set:
                continue
            for file_path in files:
                label = self._infer_algorithm_label_from_path(root_folder, file_path, metric_type)
                if self._normalize_name_for_matching(label) in {
                    self._normalize_name_for_matching(x) for x in KNOWN_METRIC_FILE_SUFFIXES
                }:
                    metric_like_labels.add(label)
                grouped_by_algorithm.setdefault(label, []).append(file_path)

        if not grouped_by_algorithm:
            return [], {}

        if metric_like_labels:
            print(
                "Warning: some series labels still look like metric names: "
                + ", ".join(sorted(metric_like_labels))
                + ". This usually means the selected input folder contains metric files at the root "
                + "without algorithm names in file names or subfolders.",
                file=sys.stderr,
            )

        self.cleanup_generated_temp_dir()
        temp_dir = tempfile.mkdtemp(prefix="sim_metrics_loader_")
        self.generated_temp_dir = temp_dir

        generated_files: list[str] = []
        alias_map: dict[str, str] = {}
        used_names: set[str] = set()

        for label, files in sorted(grouped_by_algorithm.items()):
            frames = []
            for file_path in sorted(files):
                df = self.read_csv_safe(file_path)
                if df is None:
                    continue
                df = df.copy()
                df["__source_file_type__"] = self.detect_metric_file_type(file_path) or ""
                df["__source_path__"] = file_path
                frames.append(df)
            if not frames:
                continue

            merged_df = pd.concat(frames, ignore_index=True, sort=False)
            safe_stem = self._safe_output_stem(label)
            base_stem = safe_stem
            counter = 2
            while safe_stem in used_names:
                safe_stem = f"{base_stem}_{counter}"
                counter += 1
            used_names.add(safe_stem)

            output_path = os.path.join(temp_dir, f"{safe_stem}.csv")
            merged_df.to_csv(output_path, index=False)
            generated_files.append(output_path)
            alias_map[Path(output_path).stem] = label

        return generated_files, alias_map

    def discover_metrics(self, csv_files: list[str]) -> list[str]:
        metrics = set()
        for file in csv_files:
            df = self.read_csv_safe(file)
            if df is None or "Metrics" not in df.columns:
                continue
            for metric in df["Metrics"].dropna().astype(str).tolist():
                metrics.add(metric)
        return sorted(metrics)

    def get_algo_key(self, file_path: str) -> str:
        return os.path.splitext(os.path.basename(file_path))[0]

    def get_algo_label(self, file_path: str) -> str:
        key = self.get_algo_key(file_path)
        return self.algorithm_aliases.get(key, key)

    def get_metric_label(self, metric: str) -> str:
        lang = self.args.language
        return METRIC_LABELS.get(metric, {}).get(lang, metric)

    def get_algo_line_style(self, algo_key: str) -> dict:
        base = dict(ALGO_STYLES.get(algo_key, {"color": "#4c78a8", "marker": "o", "linestyle": "solid"}))
        base.update(self.custom_line_styles.get(algo_key, {}))
        marker = base.get('marker')
        if marker in {None, 'None'}:
            base['marker'] = ''
        style_id = base.get('linestyle', 'solid')
        if isinstance(style_id, list):
            style_id = tuple(style_id)
        if isinstance(style_id, str):
            reverse_map = {'-': 'solid', '--': 'dash', '-.': 'dashdot', ':': 'dot', '': 'none'}
            style_id = reverse_map.get(style_id, style_id)
        base['linestyle'] = LINESTYLE_ID_TO_SPEC.get(style_id, style_id)
        return base

    def render_graph_text(self, key: str, **kwargs) -> str:
        lang = self.args.language
        template = self.custom_graph_texts.get(lang, {}).get(key, GRAPH_TEXT_DEFAULTS.get(lang, GRAPH_TEXT_DEFAULTS["en"]).get(key, ""))
        try:
            return template.format(**kwargs)
        except Exception:
            return template

    def get_component_label(self, component_keyword: str | None, component_metric: str | None = None, percent_mode: bool = False) -> str:
        if component_keyword is None:
            return component_metric or ""
        lang = self.args.language
        source = self.custom_percent_component_labels if percent_mode else self.custom_component_labels
        defaults = PERCENT_COMPONENT_LABEL_DEFAULTS if percent_mode else COMPONENT_LABEL_DEFAULTS
        return source.get(lang, {}).get(component_keyword, defaults.get(lang, defaults["en"]).get(component_keyword, component_keyword))

    def add_configured_legend(self, ax, handles=None, labels=None, count=None):
        position = getattr(self.args, 'legend_position', 'Inside (best)')
        if position == 'No legend':
            return None
        if count is None:
            count = len(handles or labels or [])
        kwargs = {'fontsize': self.args.legend_font, 'frameon': True}
        if handles is not None:
            kwargs['handles'] = handles
        if labels is not None:
            kwargs['labels'] = labels
        if position == 'Inside (best)':
            kwargs.update({'loc': 'best'})
        elif position == 'Inside (upper right)':
            kwargs.update({'loc': 'upper right'})
        elif position == 'Inside (upper left)':
            kwargs.update({'loc': 'upper left'})
        elif position == 'Inside (lower right)':
            kwargs.update({'loc': 'lower right'})
        elif position == 'Inside (lower left)':
            kwargs.update({'loc': 'lower left'})
        elif position == 'Inside (center right)':
            kwargs.update({'loc': 'center right'})
        elif position == 'Inside (center left)':
            kwargs.update({'loc': 'center left'})
        elif position == 'Inside (upper center)':
            kwargs.update({'loc': 'upper center'})
        elif position == 'Inside (lower center)':
            kwargs.update({'loc': 'lower center'})
        elif position == 'Inside (center)':
            kwargs.update({'loc': 'center'})
        elif position == 'Bottom (outside)':
            kwargs.update({'loc': 'upper center', 'bbox_to_anchor': (0.5, -0.10), 'ncol': max(1, min(4, count))})
        elif position == 'Top (outside)':
            kwargs.update({'loc': 'lower center', 'bbox_to_anchor': (0.5, 1.02), 'ncol': max(1, min(4, count))})
        elif position == 'Right (outside)':
            kwargs.update({'loc': 'center left', 'bbox_to_anchor': (1.02, 0.5)})
        elif position == 'Left (outside)':
            kwargs.update({'loc': 'center right', 'bbox_to_anchor': (-0.02, 0.5)})
        else:
            kwargs.update({'loc': 'best'})
        leg = ax.legend(**kwargs)
        if leg is not None and leg.get_frame() is not None:
            leg.get_frame().set_edgecolor('black')
        return leg

    def apply_tick_label_weight(self, ax):
        weight = "bold" if self.args.tick_text_bold else "normal"
        for label in list(ax.get_xticklabels()) + list(ax.get_yticklabels()):
            label.set_fontweight(weight)

    def parse_specific_loads(self, raw_text: str | None):
        raw = (raw_text or "").strip()
        if not raw:
            return None
        tokens = [tok.strip() for tok in raw.replace(";", ",").split(",") if tok.strip()]
        loads = set()
        for token in tokens:
            loads.add(int(token))
        return loads if loads else None

    def filter_series_by_loads(self, x, *arrays, selected_loads=None):
        if not selected_loads:
            return (x, *arrays)
        mask = np.array([int(v) in selected_loads for v in x], dtype=bool)
        filtered = [x[mask]]
        for arr in arrays:
            filtered.append(arr[mask])
        return tuple(filtered)

    def get_log_error_mode_key(self) -> str:
        mapping = {
            "Hide lower part when CI lower <= 0": "hide",
            "Compute interval in log scale": "log",
            "Mark truncated lower error": "mark",
        }
        return mapping.get(self.args.log_error_mode, "mark")

    def get_log_plot_epsilon(self, values_valid):
        positive_values = values_valid[values_valid > 0]
        eps_base = float(np.min(positive_values)) if positive_values.size else 1e-12
        return max(eps_base * 0.1, 1e-12)

    def compute_interval_original(self, values_valid, alpha, n_boot, rng):
        mean = np.nanmean(values_valid, axis=1)
        rep_counts = np.sum(~np.isnan(values_valid), axis=1)
        std = np.nanstd(values_valid, axis=1, ddof=1)
        lower = np.empty(mean.shape[0], dtype=float)
        upper = np.empty(mean.shape[0], dtype=float)

        if self.args.ci_method == "Bootstrap":
            for i_row, row in enumerate(values_valid):
                lo, up = bootstrap_ci_mean(row, alpha=alpha, n_boot=n_boot, rng=rng)
                lower[i_row] = lo
                upper[i_row] = up
        else:
            for i_row, (m, s, n_row) in enumerate(zip(mean, std, rep_counts)):
                if n_row < 2 or not np.isfinite(s):
                    lower[i_row] = m
                    upper[i_row] = m
                else:
                    t_val = t.ppf(1 - alpha / 2, df=int(n_row) - 1)
                    margin = t_val * s / np.sqrt(n_row)
                    lower[i_row] = m - margin
                    upper[i_row] = m + margin
        return mean, lower, upper

    def compute_interval_log_scale(self, values_valid, alpha, n_boot, rng):
        mean = np.full(values_valid.shape[0], np.nan, dtype=float)
        lower = np.full(values_valid.shape[0], np.nan, dtype=float)
        upper = np.full(values_valid.shape[0], np.nan, dtype=float)

        for i_row, row in enumerate(values_valid):
            row = np.asarray(row, dtype=float)
            row = row[np.isfinite(row) & (row > 0)]
            if row.size == 0:
                continue
            if row.size == 1:
                mean[i_row] = row[0]
                lower[i_row] = row[0]
                upper[i_row] = row[0]
                continue

            log_row = np.log10(row)
            if self.args.ci_method == "Bootstrap":
                log_mean = float(np.mean(log_row))
                n = log_row.size
                samples = rng.choice(log_row, size=(n_boot, n), replace=True)
                boot_means = samples.mean(axis=1)
                lower_log = np.percentile(boot_means, 100 * (alpha / 2))
                upper_log = np.percentile(boot_means, 100 * (1 - alpha / 2))
            else:
                log_mean = float(np.mean(log_row))
                log_std = float(np.std(log_row, ddof=1))
                t_val = t.ppf(1 - alpha / 2, df=log_row.size - 1)
                margin = t_val * log_std / np.sqrt(log_row.size)
                lower_log = log_mean - margin
                upper_log = log_mean + margin

            mean[i_row] = 10 ** log_mean
            lower[i_row] = 10 ** lower_log
            upper[i_row] = 10 ** upper_log
        return mean, lower, upper

    def compute_series_statistics(self, df_m, algo_label, init_load, step, n_rep_user, alpha, n_boot, use_log_plot=False, selected_loads=None):
        rep_cols, n_rep_real = choose_rep_columns(df_m, n_rep_user, algo_label)
        if not rep_cols or n_rep_real < 1:
            return None
        df_m = parse_rep_values(df_m.copy(), rep_cols)
        values = df_m[rep_cols].values
        x_all = init_load + step * np.arange(len(df_m))
        valid = ~np.all(np.isnan(values) | (values == 0), axis=1)
        if not np.any(valid):
            return None

        values_valid = values[valid]
        x = x_all[valid]
        rng = np.random.default_rng(12345)
        log_mode_key = self.get_log_error_mode_key()

        if use_log_plot and log_mode_key == "log":
            mean, lower, upper = self.compute_interval_log_scale(values_valid, alpha, n_boot, rng)
        else:
            mean, lower, upper = self.compute_interval_original(values_valid, alpha, n_boot, rng)

        finite = np.isfinite(mean) & np.isfinite(lower) & np.isfinite(upper)
        if not np.any(finite):
            return None

        x = x[finite]
        mean = mean[finite]
        lower = lower[finite]
        upper = upper[finite]
        values_valid = values_valid[finite]

        if selected_loads:
            x, mean, lower, upper, values_valid = self.filter_series_by_loads(
                x, mean, lower, upper, values_valid, selected_loads=selected_loads
            )
            if len(x) == 0:
                return None

        truncated = np.zeros(mean.shape[0], dtype=bool)
        lower_plot = lower.copy()
        if use_log_plot and log_mode_key in {"hide", "mark"}:
            truncated = lower <= 0
            if log_mode_key == "hide":
                lower_plot = np.where(truncated, mean, lower)
            else:
                eps = self.get_log_plot_epsilon(values_valid)
                lower_plot[truncated] = eps

        upper_plot = upper.copy()
        yerr = np.vstack([
            np.maximum(mean - lower_plot, 0.0),
            np.maximum(upper_plot - mean, 0.0),
        ])

        return {
            "x": x,
            "mean": mean,
            "lower": lower,
            "upper": upper,
            "lower_plot": lower_plot,
            "upper_plot": upper_plot,
            "yerr": yerr,
            "truncated": truncated,
            "values_valid": values_valid,
        }

    def compute_mean_series(self, file_path, metric, init_load, step, n_rep_user, selected_loads=None):
        df = self.read_csv_safe(file_path)
        if df is None or "Metrics" not in df.columns:
            return None
        df_m = df[df["Metrics"] == metric].copy().reset_index(drop=True)
        if df_m.empty:
            return None
        rep_cols, _ = choose_rep_columns(df_m, n_rep_user, self.get_algo_label(file_path))
        if not rep_cols:
            return None
        df_m = parse_rep_values(df_m.copy(), rep_cols)
        values = df_m[rep_cols].values
        loads = init_load + step * np.arange(len(df_m))
        means = np.nanmean(values, axis=1)
        finite = np.isfinite(means)
        result = pd.DataFrame({"load": loads[finite], "mean": means[finite]})
        if selected_loads:
            result = result[result["load"].astype(int).isin(selected_loads)].copy()
        return result

    def print_ci_table_to_console(self, metric, algo_label, series):
        rows = []
        print("\n" + "=" * 92)
        print(f"CI TABLE | metric={metric} | algorithm={algo_label}")
        print("-" * 92)
        print(f"{'Load':>8} | {'Mean':>14} | {'CI lower':>14} | {'CI upper':>14} | {'Truncated':>10}")
        print("-" * 92)
        for load, mean, low, up, trunc in zip(series["x"], series["mean"], series["lower"], series["upper"], series["truncated"]):
            row = {
                "metric": metric,
                "algorithm": algo_label,
                "load": int(load),
                "mean": float(mean),
                "ci_lower": float(low),
                "ci_upper": float(up),
                "truncated": bool(trunc),
            }
            rows.append(row)
            print(f"{row['load']:>8} | {row['mean']:>14.6e} | {row['ci_lower']:>14.6e} | {row['ci_upper']:>14.6e} | {str(row['truncated']):>10}")
        print("=" * 92)
        return rows

    def compute_best_by_load_rows(self, metric, best_candidates):
        rows = []
        for load in sorted(best_candidates):
            entries = [(algo, float(mean)) for algo, mean in best_candidates[load] if np.isfinite(mean)]
            if not entries:
                continue
            best_mean = min(mean for _algo, mean in entries)
            winners = sorted({algo for algo, mean in entries if np.isclose(mean, best_mean, rtol=1e-12, atol=1e-15)})
            rows.append({
                "metric": metric,
                "load": int(load),
                "best_algorithm": " / ".join(winners),
                "mean": float(best_mean),
            })
        return rows

    def print_best_by_load_table_to_console(self, metric, rows):
        if not rows:
            return
        print("\n" + "=" * 88)
        print(f"BEST ALGORITHM BY LOAD | metric={metric}")
        print("-" * 88)
        print(f"{'Load':>8} | {'Best algorithm':<56} | {'Mean':>14}")
        print("-" * 88)
        for row in rows:
            print(f"{row['load']:>8} | {row['best_algorithm']:<56} | {row['mean']:>14.6e}")
        print("=" * 88)

    def find_load_index(self, n_points: int, init_load: int, step: int, load_point: int):
        if n_points <= 0:
            return None
        loads = init_load + step * np.arange(n_points)
        return int(np.argmin(np.abs(loads - load_point)))

    # ------------------------------------------------------------------
    # Plotters
    # ------------------------------------------------------------------
    def plot_line(self, files, metric, selected_loads=None):
        fig, ax = plt.subplots(figsize=(10, 7))
        all_means = []
        all_x_values = []
        max_points = 0
        metric_dfs = {}
        best_candidates = {}
        ci_rows_total = []

        alpha = get_alpha_from_conf(self.args.confidence)
        n_boot = max(100, int(self.args.bootstrap))

        for file in files:
            df = self.read_csv_safe(file)
            if df is None or "Metrics" not in df.columns:
                continue
            df_m = df[df["Metrics"] == metric].copy().reset_index(drop=True)
            metric_dfs[file] = df_m
            max_points = max(max_points, len(df_m))

        if max_points == 0:
            raise ValueError(f"No valid data found for metric: {metric}")

        global_max_x = (self.args.init_load + self.args.load_step * np.arange(max_points))[-1]

        for file in metric_dfs:
            algo_label = self.get_algo_label(file)
            mean_series_df = self.compute_mean_series(
                file,
                metric,
                self.args.init_load,
                self.args.load_step,
                self.args.replications,
                selected_loads=selected_loads,
            )
            if mean_series_df is None or mean_series_df.empty:
                continue
            for row in mean_series_df.itertuples(index=False):
                load_value = int(row.load)
                mean_value = float(row.mean)
                if np.isfinite(mean_value):
                    best_candidates.setdefault(load_value, []).append((algo_label, mean_value))

        for file, df_m in metric_dfs.items():
            algo_key = self.get_algo_key(file)
            algo_label = self.get_algo_label(file)
            style = self.get_algo_line_style(algo_key)
            series = self.compute_series_statistics(
                df_m,
                algo_label,
                self.args.init_load,
                self.args.load_step,
                self.args.replications,
                alpha,
                n_boot,
                use_log_plot=bool(self.args.log_scale),
                selected_loads=selected_loads,
            )
            if series is None:
                continue

            x = series["x"]
            mean = series["mean"]
            yerr = series["yerr"]
            truncated = series["truncated"]

            all_x_values.extend([int(v) for v in x.tolist()])
            all_means.extend(mean.tolist())
            ci_rows_total.extend(self.print_ci_table_to_console(metric, algo_label, series))

            ax.plot(
                x,
                mean,
                color=style.get("color"),
                marker=style.get("marker"),
                linestyle=style.get("linestyle"),
                linewidth=2,
                markersize=8,
                label=algo_label,
            )
            ax.errorbar(x, mean, yerr=yerr, color=style.get("color"), linewidth=0, elinewidth=1.5, capsize=4, alpha=0.7)

            if self.args.log_scale and self.get_log_error_mode_key() == "mark" and np.any(truncated):
                mark_y = np.full(np.sum(truncated), self.get_log_plot_epsilon(series["values_valid"]), dtype=float)
                ax.scatter(x[truncated], mark_y, marker="v", s=55, facecolors="white", edgecolors=style.get("color"), linewidths=1.2, zorder=5)

        if not all_means:
            raise ValueError(f"No valid data found for metric: {metric}")

        if self.args.log_scale:
            positive_means = [m for m in all_means if m > 0]
            if not positive_means:
                raise ValueError("Cannot use log scale with all zero values.")
            ymin_positive = min(positive_means)
            ymax_positive = max(positive_means)
            ymin_power = np.floor(np.log10(ymin_positive))
            ymax_power = np.ceil(np.log10(ymax_positive))
            ymin = 10 ** ymin_power
            ymax = 10 ** ymax_power
            if ymin_positive / ymin < 0.1:
                ymin = 10 ** (ymin_power - 1)
            ax.set_yscale("log")
            ylabel = self.render_graph_text("line_y_log", metric=self.get_metric_label(metric), load="")
            ax.set_ylim(ymin * self.args.y_bottom_margin_log, ymax * self.args.y_top_margin_log)
        else:
            ymin = min(all_means)
            ymax = max(all_means)
            if np.isclose(ymin, ymax):
                ymax = ymin + 1e-9
            ylabel = self.render_graph_text("line_y", metric=self.get_metric_label(metric), load="")
            ax.set_ylim(max(0.0, ymin - self.args.y_bottom_margin_linear), ymax + self.args.y_top_margin_linear)

        # Match the GUI behavior: the X axis must be based on the real plotted
        # load values, not forced to zero. For the default artifact data this
        # keeps the first visible load around 500 Erlangs instead of starting at 0.
        xmin_real = min(all_x_values)
        xmax_real = max(all_x_values)
        xmin_adjusted = min(self.args.init_load, xmin_real - self.args.x_left_margin)
        xmax_adjusted = max(global_max_x, xmax_real + self.args.x_right_margin)
        ax.set_xlim(xmin_adjusted, xmax_adjusted)
        ax.set_xticks(sorted(set(all_x_values)))
        print(f"X axis - real load values: {xmin_real} to {xmax_real}")
        print(f"Applied X margins: left={self.args.x_left_margin} Erlangs, right={self.args.x_right_margin} Erlangs")

        ax.set_xlabel(self.render_graph_text("line_x", metric=self.get_metric_label(metric), load=""), fontsize=self.args.axis_font, fontweight="bold" if self.args.axis_text_bold else "normal")
        ax.set_ylabel(ylabel, fontsize=self.args.axis_font, fontweight="bold" if self.args.axis_text_bold else "normal")
        title = self.render_graph_text("line_title", metric=self.get_metric_label(metric), load="")
        if title.strip():
            ax.set_title(title, fontsize=self.args.axis_font + 2, pad=20)
        ax.tick_params(axis="both", labelsize=self.args.tick_font)
        self.apply_tick_label_weight(ax)
        ax.grid(True, which="major", axis="both", alpha=0.35)
        if self.args.y_grid_mode == "Many lines (major + minor)" and self.args.log_scale:
            ax.grid(True, which="minor", axis="y", alpha=0.20)
        self.add_configured_legend(ax)
        fig.tight_layout(pad=2.0)

        best_rows = self.compute_best_by_load_rows(metric, best_candidates)
        self.print_best_by_load_table_to_console(metric, best_rows)
        return fig, ci_rows_total, best_rows

    def plot_stacked_bars(self, files, components):
        fig, ax = plt.subplots(figsize=(12, 8))
        algo_names = []
        component_data = {comp: [] for comp in components}

        for file in files:
            algo_name = self.get_algo_label(file)
            algo_names.append(algo_name)
            df = self.read_csv_safe(file)
            if df is None or "Metrics" not in df.columns:
                for comp in components:
                    component_data[comp].append(0.0)
                continue
            for comp in components:
                df_comp = df[df["Metrics"] == comp].copy().reset_index(drop=True)
                load_idx = self.find_load_index(len(df_comp), self.args.init_load, self.args.load_step, self.args.load_point)
                if len(df_comp) > 0 and load_idx is not None and load_idx < len(df_comp):
                    rep_cols, _ = choose_rep_columns(df_comp, self.args.replications, algo_name)
                    if not rep_cols:
                        component_data[comp].append(0.0)
                        continue
                    df_comp = parse_rep_values(df_comp.copy(), rep_cols)
                    values = df_comp[rep_cols].values
                    mean_value = float(np.nanmean(values[load_idx])) if len(values) > load_idx else 0.0
                    component_data[comp].append(mean_value if np.isfinite(mean_value) else 0.0)
                else:
                    component_data[comp].append(0.0)

        total_data = sum(sum(vals) for vals in component_data.values())
        if total_data == 0:
            raise ValueError(f"No data found for the selected components at load point {self.args.load_point}.")

        from matplotlib.patches import Patch
        x_pos = np.arange(len(algo_names))
        width = 0.7
        bottom = np.zeros(len(algo_names))
        legend_elements = []

        for comp in get_component_plot_order(components):
            values = np.asarray(component_data[comp], dtype=float)
            component_keyword = match_component_keyword(comp)
            if component_keyword:
                comp_color = COMPONENT_COLORS.get(component_keyword)
                legend_label = self.get_component_label(component_keyword, comp, percent_mode=False)
            else:
                color_idx = hash(comp) % 10
                comp_color = plt.cm.tab20(color_idx / 10)
                legend_label = comp
            ax.bar(x_pos, values, width, bottom=bottom, color=comp_color, edgecolor="black")
            legend_elements.append(Patch(facecolor=comp_color, edgecolor="black", label=legend_label))
            bottom += values

        metric_label = self.get_metric_label("BlockingProbability")
        ax.set_xlabel(self.render_graph_text("stacked_bar_x", metric=metric_label, load=self.args.load_point), fontsize=self.args.axis_font, fontweight="bold" if self.args.axis_text_bold else "normal")
        ax.set_ylabel(self.render_graph_text("stacked_bar_y", metric=metric_label, load=self.args.load_point), fontsize=self.args.axis_font, fontweight="bold" if self.args.axis_text_bold else "normal")
        title = self.render_graph_text("stacked_bar_title", metric=metric_label, load=self.args.load_point)
        if title.strip():
            ax.set_title(title, fontsize=self.args.axis_font + 2, pad=20)
        ax.set_xticks(x_pos)
        ax.set_xticklabels(algo_names, fontsize=self.args.tick_font, rotation=45)
        ax.tick_params(axis="y", labelsize=self.args.tick_font)
        self.apply_tick_label_weight(ax)
        ax.set_ylim(max(0, -self.args.y_bottom_margin_linear), float(np.max(bottom)) + self.args.y_top_margin_linear)
        x_margin = self.args.x_margin_bar * width
        ax.set_xlim(-x_margin, len(algo_names) - 1 + x_margin)
        self.add_configured_legend(ax, handles=legend_elements, count=len(legend_elements))
        ax.grid(True, axis="y", alpha=0.3)
        fig.tight_layout(pad=2.0)
        return fig

    def plot_stacked_percent_bars(self, files, components):
        fig, ax = plt.subplots(figsize=(12, 8))
        algo_names = []
        component_data = {comp: [] for comp in components}

        for file in files:
            algo_name = self.get_algo_label(file)
            algo_names.append(algo_name)
            df = self.read_csv_safe(file)
            if df is None or "Metrics" not in df.columns:
                for comp in components:
                    component_data[comp].append(0.0)
                continue
            for comp in components:
                df_comp = df[df["Metrics"] == comp].copy().reset_index(drop=True)
                load_idx = self.find_load_index(len(df_comp), self.args.init_load, self.args.load_step, self.args.load_point)
                if len(df_comp) > 0 and load_idx is not None and load_idx < len(df_comp):
                    rep_cols, _ = choose_rep_columns(df_comp, self.args.replications, algo_name)
                    if not rep_cols:
                        component_data[comp].append(0.0)
                        continue
                    df_comp = parse_rep_values(df_comp.copy(), rep_cols)
                    values = df_comp[rep_cols].values
                    mean_value = float(np.nanmean(values[load_idx])) if len(values) > load_idx else 0.0
                    component_data[comp].append(mean_value if np.isfinite(mean_value) else 0.0)
                else:
                    component_data[comp].append(0.0)

        totals = np.zeros(len(algo_names), dtype=float)
        for comp in components:
            totals += np.asarray(component_data[comp], dtype=float)
        if np.allclose(totals, 0.0):
            raise ValueError(f"No data found for the selected components at load point {self.args.load_point}.")

        from matplotlib.patches import Patch
        x_pos = np.arange(len(algo_names))
        width = 0.7
        bottom = np.zeros(len(algo_names), dtype=float)
        legend_elements = []

        for comp in get_component_plot_order(components):
            raw_values = np.asarray(component_data[comp], dtype=float)
            values_pct = np.divide(raw_values * 100.0, totals, out=np.zeros_like(raw_values), where=totals > 0)
            component_keyword = match_component_keyword(comp)
            if component_keyword:
                comp_color = COMPONENT_COLORS.get(component_keyword)
                legend_label = self.get_component_label(component_keyword, comp, percent_mode=True)
            else:
                color_idx = hash(comp) % 10
                comp_color = plt.cm.tab20(color_idx / 10)
                legend_label = comp
            ax.bar(x_pos, values_pct, width, bottom=bottom, color=comp_color, edgecolor="white", linewidth=0.4)
            legend_elements.append(Patch(facecolor=comp_color, edgecolor="white", label=legend_label))
            bottom += values_pct

        metric_label = self.get_metric_label("BlockingProbability")
        ax.set_xlabel(self.render_graph_text("percent_bar_x", metric=metric_label, load=self.args.load_point), fontsize=self.args.axis_font, fontweight="bold" if self.args.axis_text_bold else "normal")
        ax.set_ylabel(self.render_graph_text("percent_bar_y", metric=metric_label, load=self.args.load_point), fontsize=self.args.axis_font, fontweight="bold" if self.args.axis_text_bold else "normal")
        title = self.render_graph_text("percent_bar_title", metric=metric_label, load=self.args.load_point)
        if title.strip():
            ax.set_title(title, fontsize=self.args.axis_font + 2, pad=20)
        ax.set_xticks(x_pos)
        ax.set_xticklabels(algo_names, fontsize=self.args.tick_font, rotation=0)
        ax.set_yticks(np.arange(0, 101, 10))
        ax.set_yticklabels([f"{v}%" for v in range(0, 101, 10)], fontsize=self.args.tick_font)
        self.apply_tick_label_weight(ax)
        ax.set_ylim(0, 100)
        x_margin = self.args.x_margin_bar * width
        ax.set_xlim(-x_margin, len(algo_names) - 1 + x_margin)
        self.add_configured_legend(ax, handles=legend_elements, count=len(legend_elements))
        ax.grid(True, axis="y", alpha=0.3)
        fig.tight_layout(pad=2.0)
        return fig


# ----------------------------------------------------------------------
# CLI parser and main routine
# ----------------------------------------------------------------------
def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Generate simulation plots from CSV folders without using the Tkinter GUI.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--input", help="Root folder containing CSV files or topology subfolders.")
    parser.add_argument("--metric-type", action="append", default=[], help="Metric file type to scan (can be repeated).")
    parser.add_argument("--metric", help="Metric name from the Metrics column. Required for line plots.")
    parser.add_argument("--plot", choices=["line", "bar"], default="line", help="Plot family to generate.")
    parser.add_argument("--bar-mode", choices=["absolute", "percent"], default="absolute", help="Bar plot mode.")
    parser.add_argument("--components", default="", help="Comma-separated component metrics for bar plots.")
    parser.add_argument("--init-load", type=int, default=500, help="Initial load value.")
    parser.add_argument("--load-step", type=int, default=250, help="Load increment.")
    parser.add_argument("--replications", type=int, default=0, help="Number of replications to use. 0 means auto-detect.")
    parser.add_argument("--load-point", type=int, default=500, help="Load point for bar plots.")
    parser.add_argument("--load-filter", default="", help="Optional comma-separated load filter for line plots.")
    parser.add_argument("--confidence", default="95%", choices=["90%", "95%", "99%"], help="Confidence level.")
    parser.add_argument("--ci-method", default="t-Student", choices=["t-Student", "Bootstrap"], help="Confidence interval method.")
    parser.add_argument("--bootstrap", type=int, default=2000, help="Bootstrap resamples when Bootstrap is selected.")
    parser.add_argument("--language", default="en", choices=["en", "pt"], help="Language for plot labels.")
    parser.add_argument("--log-scale", action="store_true", help="Use log scale in line plots.")
    parser.add_argument("--log-error-mode", default="Mark truncated lower error", choices=[
        "Hide lower part when CI lower <= 0",
        "Compute interval in log scale",
        "Mark truncated lower error",
    ], help="Handling of lower CI values in log scale.")
    parser.add_argument("--y-grid-mode", default="Many lines (major + minor)", choices=[
        "Many lines (major + minor)",
        "Major lines only",
    ], help="Y-axis grid style.")
    parser.add_argument("--axis-font", type=int, default=12, help="Axis label font size.")
    parser.add_argument("--tick-font", type=int, default=11, help="Tick font size.")
    parser.add_argument("--legend-font", type=int, default=11, help="Legend font size.")
    parser.add_argument("--legend-position", default="Inside (best)", choices=[
        "Inside (best)",
        "Inside (upper right)",
        "Inside (upper left)",
        "Inside (lower right)",
        "Inside (lower left)",
        "Inside (center right)",
        "Inside (center left)",
        "Inside (upper center)",
        "Inside (lower center)",
        "Inside (center)",
        "Bottom (outside)",
        "Top (outside)",
        "Right (outside)",
        "Left (outside)",
        "No legend",
    ], help="Legend placement.")
    parser.add_argument("--axis-text-bold", action="store_true", help="Use bold axis labels.")
    parser.add_argument("--tick-text-bold", action="store_true", help="Use bold tick labels.")
    parser.add_argument("--x-left-margin", type=float, default=50.0, help="Left X margin for line plots.")
    parser.add_argument("--x-right-margin", type=float, default=50.0, help="Right X margin for line plots.")
    parser.add_argument("--x-margin-bar", type=float, default=1.0, help="Horizontal bar chart margin factor.")
    parser.add_argument("--y-bottom-margin-linear", type=float, default=0.01, help="Bottom Y margin in linear scale.")
    parser.add_argument("--y-top-margin-linear", type=float, default=0.01, help="Top Y margin in linear scale.")
    parser.add_argument("--y-bottom-margin-log", type=float, default=0.8, help="Bottom Y multiplier in log scale.")
    parser.add_argument("--y-top-margin-log", type=float, default=1.2, help="Top Y multiplier in log scale.")
    parser.add_argument("--settings-json", help="GUI settings JSON saved by Save customizations..., used as a base configuration.")
    parser.add_argument("--config", help="Compact CLI JSON configuration. Applied after --settings-json and before explicit CLI flags.")
    parser.add_argument("--aliases-json", help="Optional JSON file with algorithm aliases.")
    parser.add_argument("--line-styles-json", help="Optional JSON file with line style overrides.")
    parser.add_argument("--graph-texts-json", help="Optional JSON file with plot text overrides.")
    parser.add_argument("--component-labels-json", help="Optional JSON file with component legend overrides.")
    parser.add_argument("--percent-component-labels-json", help="Optional JSON file with percent component legend overrides.")
    parser.add_argument("--output", help="Output image path (.svg, .pdf, .png, etc.).")
    parser.add_argument("--ci-output", help="Optional CSV file for the CI table.")
    parser.add_argument("--best-output", help="Optional CSV file for the best-algorithm-by-load table.")
    parser.add_argument("--list-metrics", action="store_true", help="List discovered metrics and exit without generating a plot.")
    return parser


def ensure_parent_dir(path_str: str):
    path = Path(path_str)
    path.parent.mkdir(parents=True, exist_ok=True)


def cli_main(argv: list[str] | None = None) -> int:
    if argv is None:
        argv = sys.argv[1:]
    argv = [arg for arg in argv if arg != "--cli"]
    parser = build_parser()
    args = parser.parse_args(argv)
    args = apply_settings_json_to_args(args, argv)
    args = apply_compact_config_to_args(args, argv)

    if not args.input:
        parser.error("Missing input folder. Provide --input or set it in --config.")
    if not args.output:
        parser.error("Missing output path. Provide --output or set it in --config.")

    analyzer = HeadlessAnalyzer(args)
    try:
        csv_files, alias_map = analyzer.build_merged_csvs_from_folder(args.input, args.metric_type)
        if not csv_files:
            raise ValueError("No metric CSV files were found in the selected folder.")

        analyzer.algorithm_aliases = {**alias_map, **analyzer.algorithm_aliases}
        discovered_metrics = analyzer.discover_metrics(csv_files)
        if not discovered_metrics:
            raise ValueError("No metrics were found in the merged CSV files.")

        if getattr(args, "list_metrics", False):
            print("Available metrics:")
            for metric_name in discovered_metrics:
                print(f"  - {metric_name}")
            return 0

        selected_loads = analyzer.parse_specific_loads(args.load_filter)

        if args.plot == "line":
            selected_metric = resolve_metric_name(args.metric, discovered_metrics)
            if selected_metric is None:
                raise ValueError(
                    f"Metric '{args.metric}' was not found. Available metrics: {', '.join(discovered_metrics)}"
                )
            if args.metric != selected_metric:
                print(f"Resolved metric '{args.metric}' -> '{selected_metric}'")
            fig, ci_rows, best_rows = analyzer.plot_line(csv_files, selected_metric, selected_loads=selected_loads)
            ensure_parent_dir(args.output)
            fig.savefig(args.output, bbox_inches="tight")
            plt.close(fig)
            print(f"Saved plot to: {args.output}")
            if args.ci_output:
                ensure_parent_dir(args.ci_output)
                pd.DataFrame(ci_rows).to_csv(args.ci_output, index=False)
                print(f"Saved CI table to: {args.ci_output}")
            if args.best_output:
                ensure_parent_dir(args.best_output)
                pd.DataFrame(best_rows).to_csv(args.best_output, index=False)
                print(f"Saved best-by-load table to: {args.best_output}")
        else:
            components = [c.strip() for c in args.components.split(",") if c.strip()]
            if not components:
                raise ValueError("For bar plots, provide --components with a comma-separated list.")
            components = resolve_component_names(components, discovered_metrics)
            print("Using component metrics: " + ", ".join(components))
            if args.bar_mode == "percent":
                fig = analyzer.plot_stacked_percent_bars(csv_files, components)
            else:
                fig = analyzer.plot_stacked_bars(csv_files, components)
            ensure_parent_dir(args.output)
            fig.savefig(args.output, bbox_inches="tight")
            plt.close(fig)
            print(f"Saved plot to: {args.output}")
    finally:
        analyzer.cleanup_generated_temp_dir()

    return 0




# ======================================================
# Unified entry point
# ======================================================
def _should_run_cli(argv: list[str]) -> bool:
    return any(
        arg == "--cli"
        or arg == "--config"
        or arg.startswith("--config=")
        or arg == "--settings-json"
        or arg.startswith("--settings-json=")
        or arg == "--input"
        or arg.startswith("--input=")
        or arg == "--list-metrics"
        for arg in argv
    )


if __name__ == "__main__":
    if _should_run_cli(sys.argv[1:]):
        raise SystemExit(cli_main(sys.argv[1:]))

    if not _TK_AVAILABLE:
        raise SystemExit(
            "Tkinter is not available. Install Tkinter to run the GUI, "
            "or use CLI mode with --config/--input."
        )

    root = tk.Tk()
    app = GenericAnalyzerGUI(root)
    root.mainloop()
