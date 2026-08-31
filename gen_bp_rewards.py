#!/usr/bin/env python3
"""Generate rewards.yml for the Battle Pass season (100 levels, free + premium).

Autumn 2026 season, tech-modpack flavour:
- Rewards are mostly MODDED items (pulled from the validated Void-Upgrader pool: installed
  mods, real ids, Russian names, with icons) delivered via COMMAND give with an `icon:` so
  the WebGUI shows the texture. Vanilla only fills a few early basics + money.
- Progression by tier: early = common/rare, mid = rare/epic, late = epic/legendary. High
  levels (80-100) give genuinely valuable endgame items (no "64 obsidian at level 99").
- NO EXP rewards. Void Coin: free track at 10 & 50; premium every 10th level (100 → 1000).

Run with the backend venv:  minecraft_backend/.venv/bin/python voidrp_battlepass/gen_bp_rewards.py
"""
import random
import sys
from pathlib import Path

BACKEND = Path(__file__).resolve().parent.parent / "minecraft_backend"
sys.path.insert(0, str(BACKEND))

from sqlalchemy import select  # noqa: E402

from apps.api.app.db import SessionLocal  # noqa: E402
from apps.api.app.models.game_server import GameServer  # noqa: E402
from apps.api.app.models.void_upgrader import VoidUpgraderReward  # noqa: E402

OUT = Path(__file__).resolve().parent / "src" / "main" / "resources" / "rewards.yml"

VOIDCOIN_PREM = {10: 100, 20: 120, 30: 140, 40: 160, 50: 200, 60: 240, 70: 280, 80: 320, 90: 400, 100: 1000}
VOIDCOIN_FREE = {10: 30, 50: 60}   # a taste of the premium currency on the free track

# a few vanilla basics for the earliest levels (ITEM type, nice stacks)
VANILLA_EARLY = [
    ("COOKED_BEEF", 16, "Стейк ×16"), ("COAL", 32, "Уголь ×32"), ("IRON_INGOT", 16, "Железный слиток ×16"),
    ("ARROW", 64, "Стрелы ×64"), ("GOLD_INGOT", 8, "Золотой слиток ×8"), ("EXPERIENCE_BOTTLE", 16, "Бутыль опыта ×16"),
    ("ENDER_PEARL", 8, "Жемчуг Края ×8"), ("DIAMOND", 4, "Алмаз ×4"),
]


# crafting intermediates — you build WITH these, they're not desirable rewards
INTERMEDIATE = ("mold", "cast", "press", "plate", "sheet", "coil", "wire", "fitting", "casing",
                "gearbox", "gear", "rod", "grit", "slurry", "dust", "nugget", "fragment", "chunk",
                "scrap", "servo", "wafer", "cable", "conduit", "pipe", "dielectric", "compound",
                "mixture", "solution", "substrate", "filament", "bristle", "sawdust", "pulp",
                "wireless_", "mechanical_", "raw_", "crushed", "clump", "shard_of")


def load_modded_pools():
    """Modded upgrader items with icons, grouped by tier (cheapest first), excluding crafting
    intermediates. Only rare/epic/legendary — cheap 'common' components make poor rewards."""
    s = SessionLocal()
    try:
        srv = s.execute(select(GameServer).where(GameServer.is_default.is_(True))).scalar_one()
        rows = s.execute(
            select(VoidUpgraderReward).where(
                VoidUpgraderReward.server_id == srv.id,
                VoidUpgraderReward.enabled.is_(True),
            )
        ).scalars().all()
    finally:
        s.close()
    pools = {"rare": [], "epic": [], "legendary": []}
    for r in rows:
        if r.item_key.startswith("minecraft:"):
            continue                                  # vanilla handled as ITEM
        if r.tier not in pools:
            continue                                  # skip 'common' junk tier
        path = r.item_key.split(":", 1)[-1]
        if any(k in path for k in INTERMEDIATE):
            continue                                  # skip crafting intermediates
        pools[r.tier].append((r.item_key, r.display_name, int(r.vc_value)))
    rng = random.Random(2026)
    for k in pools:
        pools[k].sort(key=lambda x: x[2])
        rng.shuffle(pools[k])
    return pools


POOLS = load_modded_pools()
_cur = {"rare": 0, "epic": 0, "legendary": 0}


def take(tier):
    pool = POOLS.get(tier) or POOLS["rare"] or POOLS["epic"]
    it = pool[_cur.get(tier, 0) % len(pool)]
    _cur[tier] = _cur.get(tier, 0) + 1
    return it


def band(level):
    """Reward tier by level. Rare covers most (biggest pool); epic/legendary at the top.
    Free & premium at the same level draw DIFFERENT items (shared cursor), so premium value
    comes from x2 counts + the Void Coin milestones, not from exhausting the small epic pool."""
    if level <= 55:   return "rare"
    if level <= 85:   return "epic"
    return "legendary"


def esc(s):
    return (s or "").replace('"', '\\"')


def money_amt(level, premium):
    v = round((900 + level * 90) / 500) * 500
    return v * 2 if premium else v


def modded_reward(tier, premium):
    item_key, name, vc = take(tier)
    cnt = 2 if (premium and vc < 120) else 1
    disp = name + (f" ×{cnt}" if cnt > 1 else "")
    return (f'{{type: COMMAND, command: "/minecraft:give {{player}} {item_key} {cnt}", '
            f'displayName: "{esc(disp)}", icon: "{item_key}"}}')


def gen_track(premium):
    vc_map = VOIDCOIN_PREM if premium else VOIDCOIN_FREE
    lines = []
    vi = 0
    for lvl in range(1, 101):
        if lvl in vc_map:
            r = f"{{type: VOIDCOIN, amount: {vc_map[lvl]}}}"
        elif lvl <= 12 and lvl % 3 == 2 and vi < len(VANILLA_EARLY):
            mat, cnt, nm = VANILLA_EARLY[vi]; vi += 1
            if premium:
                cnt = min(cnt * 2, 64); nm = nm.split(" ×")[0] + (f" ×{cnt}" if cnt > 1 else "")
            r = f'{{type: ITEM, material: {mat}, count: {cnt}, displayName: "{esc(nm)}", icon: "minecraft:{mat.lower()}"}}'
        elif lvl % 4 == 0:
            r = f"{{type: MONEY, amount: {money_amt(lvl, premium)}}}"
        else:
            r = modded_reward(band(lvl), premium)
        lines.append(f"  {lvl}: {r}")
    return "\n".join(lines)


def main():
    out = ["# VoidRP Battle Pass — Осенний сезон 2026 — 100 уровней",
           "# Сгенерировано gen_bp_rewards.py. Моды из выверенного пула, у каждого icon для WebGUI.",
           "# Без EXP; Void Coin: free 10/50, premium каждый 10-й (100→1000).",
           "free:", gen_track(False), "premium:", gen_track(True), ""]
    OUT.write_text("\n".join(out), encoding="utf-8")
    print(f"wrote {OUT}")
    print("modded pool sizes:", {k: len(v) for k, v in POOLS.items()})


if __name__ == "__main__":
    main()
