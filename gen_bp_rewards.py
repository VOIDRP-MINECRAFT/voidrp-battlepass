#!/usr/bin/env python3
"""Generate rewards.yml for the Battle Pass season (100 levels, free + premium).

Rules for the Autumn 2026 season:
- NO EXP rewards (XP raises the BP level itself → pointless as a reward).
- Free track: money + vanilla items + occasional modded (COMMAND) items, scaling with level.
- Premium track: better money/items + Void Coin on every 10th ("significant") level, 100 → up.
Modded ids are the validated ftbevolution ones already used in the pack.

Run:  python3 gen_bp_rewards.py  (writes src/main/resources/rewards.yml)
"""
from pathlib import Path

OUT = Path(__file__).resolve().parent / "src" / "main" / "resources" / "rewards.yml"

# Void Coin milestones on premium (every 10th level), starting at 100 and climbing.
VOIDCOIN = {10: 100, 20: 120, 30: 140, 40: 160, 50: 200, 60: 240, 70: 280, 80: 320, 90: 400, 100: 1000}

# vanilla item pools by tier: (MATERIAL, count, ru_name)
LOW_ITEMS = [
    ("COOKED_BEEF", 16, "Стейк ×16"), ("BREAD", 32, "Хлеб ×32"), ("COAL", 32, "Уголь ×32"),
    ("IRON_INGOT", 16, "Железный слиток ×16"), ("ARROW", 64, "Стрелы ×64"), ("TORCH", 48, "Факелы ×48"),
    ("STRING", 16, "Нить ×16"), ("REDSTONE", 32, "Редстоун ×32"), ("GOLD_INGOT", 8, "Золотой слиток ×8"),
    ("LEATHER", 16, "Кожа ×16"), ("SLIME_BALL", 8, "Слизь ×8"), ("GLOWSTONE", 16, "Светокамень ×16"),
    ("OAK_LOG", 32, "Дубовые брёвна ×32"), ("GUNPOWDER", 16, "Порох ×16"),
]
MID_ITEMS = [
    ("DIAMOND", 4, "Алмаз ×4"), ("EMERALD", 8, "Изумруд ×8"), ("IRON_BLOCK", 3, "Железный блок ×3"),
    ("GOLD_BLOCK", 2, "Золотой блок ×2"), ("ENDER_PEARL", 8, "Жемчуг Края ×8"), ("BLAZE_ROD", 8, "Огненный стержень ×8"),
    ("OBSIDIAN", 16, "Обсидиан ×16"), ("EXPERIENCE_BOTTLE", 16, "Бутыль опыта ×16"), ("LAPIS_BLOCK", 4, "Блок лазурита ×4"),
    ("QUARTZ", 32, "Кварц ×32"), ("HONEY_BLOCK", 8, "Медовый блок ×8"), ("NAME_TAG", 2, "Бирка ×2"),
    ("SADDLE", 1, "Седло"), ("GHAST_TEAR", 4, "Слеза гаста ×4"), ("PHANTOM_MEMBRANE", 8, "Мембрана фантома ×8"),
]
HIGH_ITEMS = [
    ("DIAMOND_BLOCK", 2, "Блок алмаза ×2"), ("NETHERITE_SCRAP", 2, "Незеритовый лом ×2"),
    ("ENCHANTED_GOLDEN_APPLE", 2, "Зачарованное яблоко ×2"), ("SHULKER_SHELL", 4, "Панцирь шалкера ×4"),
    ("DIAMOND", 16, "Алмаз ×16"), ("EMERALD_BLOCK", 3, "Блок изумруда ×3"), ("TOTEM_OF_UNDYING", 2, "Тотем бессмертия ×2"),
    ("ANCIENT_DEBRIS", 4, "Древние обломки ×4"), ("GOLD_BLOCK", 6, "Золотой блок ×6"), ("OBSIDIAN", 32, "Обсидиан ×32"),
]
# premium-only bigger vanilla items
PREM_HIGH = [
    ("NETHERITE_INGOT", 2, "Незеритовый слиток ×2"), ("BEACON", 1, "Маяк"), ("ELYTRA", 1, "Элитры"),
    ("NETHER_STAR", 1, "Незвёздная звезда"), ("NETHERITE_BLOCK", 1, "Блок незерита"), ("SHULKER_BOX", 2, "Шалкеровый ящик ×2"),
    ("ENCHANTED_GOLDEN_APPLE", 4, "Зачарованное яблоко ×4"), ("DIAMOND_BLOCK", 4, "Блок алмаза ×4"),
]

# modded (COMMAND give) pools by tier: (id, count, ru_name)
LOW_MOD = [("ftbevolution:xy_aluminum_dust", 2, "Ксилюминиевая пыль ×2"), ("ftbevolution:ruby_gem", 1, "Рубин"),
           ("ftbevolution:genetic_substrate", 1, "Генетический субстрат"), ("ftbevolution:bio_neural_circuit", 1, "Био-нейронная схема"),
           ("ftbevolution:ender_apple", 2, "Эндерное яблоко ×2"), ("ftbevolution:sands_of_time", 1, "Пески времени")]
MID_MOD = [("ftbevolution:eclipse_alloy_plate", 2, "Пластина сплава Затмения ×2"), ("ftbevolution:crystalline_element", 1, "Кристаллический элемент"),
           ("ftbevolution:elemental_arcanite", 1, "Элементальный арканит"), ("ftbevolution:refined_nitro_crystal", 1, "Очищенный нитро-кристалл"),
           ("ftbevolution:eclipse_alloy_large_plate", 1, "Большая пластина Затмения"), ("ftbevolution:prediction_amalgam", 1, "Амальгама предсказаний"),
           ("ftbevolution:ender_transmitter", 1, "Эндер-передатчик"), ("ftbevolution:fortron_infused_large_plate", 1, "Фортрон-пластина")]
HIGH_MOD = [("ftbevolution:awakened_crystalline_shard", 1, "Пробуждённый кристаллический осколок"), ("ftbevolution:evolutionary_matter", 1, "Эволюционная материя"),
            ("ftbevolution:primal_essence", 1, "Первичная эссенция"), ("ftbevolution:evolutionary_arcanum", 1, "Эволюционный арканум"),
            ("ftbevolution:black_star", 1, "Чёрная звезда"), ("ftbevolution:supernova", 1, "Сверхновая"),
            ("ftbevolution:realized_transcendence", 1, "Осознанное превосходство"), ("ftbevolution:ultimate_singularity", 1, "Абсолютная сингулярность")]


def tier(level):
    return 0 if level <= 33 else (1 if level <= 66 else 2)


def item_pool(t):    return [LOW_ITEMS, MID_ITEMS, HIGH_ITEMS][t]
def mod_pool(t):     return [LOW_MOD, MID_MOD, HIGH_MOD][t]


def money_free(level):
    return round((800 + level * 80) / 500) * 500


def esc(s):
    return s.replace('"', '\\"')


def gen_track(premium):
    lines = []
    # rotating cursors so items/mod don't repeat back-to-back
    ic = [0, 0, 0]; mc = [0, 0, 0]
    for lvl in range(1, 101):
        t = tier(lvl)
        r = None
        if premium and lvl in VOIDCOIN:
            r = f"{{type: VOIDCOIN, amount: {VOIDCOIN[lvl]}}}"
        elif lvl % 10 == 5:                                   # modded item every _5 level
            pool = mod_pool(t); mid, cnt, nm = pool[mc[t] % len(pool)]; mc[t] += 1
            cnt = cnt * (2 if premium else 1)
            r = f'{{type: COMMAND, command: "/minecraft:give {{player}} {mid} {cnt}", displayName: "{esc(nm)}"}}'
        elif premium and lvl == 100:
            mat, cnt, nm = PREM_HIGH[0]; r = f'{{type: ITEM, material: {mat}, count: {cnt}, displayName: "{esc(nm)}"}}'
        elif premium and t == 2 and lvl % 4 == 0:            # premium high-tier bonus items
            pool = PREM_HIGH; mat, cnt, nm = pool[ic[t] % len(pool)]; ic[t] += 1
            r = f'{{type: ITEM, material: {mat}, count: {cnt}, displayName: "{esc(nm)}"}}'
        elif lvl % 3 == 1:                                   # money
            amt = money_free(lvl) * (2 if premium else 1)
            r = f"{{type: MONEY, amount: {amt}}}"
        else:                                                # vanilla item
            pool = item_pool(t); mat, cnt, nm = pool[ic[t] % len(pool)]; ic[t] += 1
            if premium:
                cnt = min(cnt * 2, 64)
                nm = nm.split(" ×")[0] + (f" ×{cnt}" if cnt > 1 else "")
            r = f'{{type: ITEM, material: {mat}, count: {cnt}, displayName: "{esc(nm)}"}}'
        lines.append(f"  {lvl}: {r}")
    return "\n".join(lines)


def main():
    out = ["# VoidRP Battle Pass — Осенний сезон 2026 — 100 уровней",
           "# Сгенерировано gen_bp_rewards.py. Без EXP-наград; Void Coin на значимых уровнях премиума.",
           "free:", gen_track(False), "premium:", gen_track(True), ""]
    OUT.write_text("\n".join(out), encoding="utf-8")
    print(f"wrote {OUT} (100 levels free + premium, no EXP, voidcoin milestones)")


if __name__ == "__main__":
    main()
