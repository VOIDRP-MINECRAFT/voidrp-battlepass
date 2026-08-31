package ru.voidrp.battlepass.quest;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

public final class BpQuestPool {

    private static final List<BpQuestTemplate> FREE_POOL = new ArrayList<>();
    private static final List<BpQuestTemplate> PREMIUM_POOL = new ArrayList<>();

    static {
        // ── Free quests ──
        FREE_POOL.add(new BpQuestTemplate("kill_any_50",    "Охотник",         "Убить 50 любых мобов",           BpQuestType.KILL,    "any",                     50,  500,  false));
        FREE_POOL.add(new BpQuestTemplate("kill_zombies",   "Зомби-апокалипсис","Убить 30 зомби",                BpQuestType.KILL,    "minecraft:zombie",        30,  400,  false));
        FREE_POOL.add(new BpQuestTemplate("kill_skeletons", "Костолом",        "Убить 30 скелетов",              BpQuestType.KILL,    "minecraft:skeleton",      30,  400,  false));
        FREE_POOL.add(new BpQuestTemplate("mine_stone",     "Горняк",          "Добыть 128 камня",               BpQuestType.MINE,    "STONE",                   128, 400,  false));
        FREE_POOL.add(new BpQuestTemplate("mine_coal",      "Угольщик",        "Добыть 64 угля",                 BpQuestType.MINE,    "COAL_ORE",                64,  400,  false));
        FREE_POOL.add(new BpQuestTemplate("mine_iron",      "Железный путь",   "Добыть 32 железа",               BpQuestType.MINE,    "IRON_ORE",                32,  500,  false));
        FREE_POOL.add(new BpQuestTemplate("collect_wood",   "Лесоруб",         "Собрать 64 любых бревна",        BpQuestType.COLLECT, "OAK_LOG",                 64,  400,  false));
        FREE_POOL.add(new BpQuestTemplate("fish_any",       "Рыболов",         "Поймать 10 рыб",                 BpQuestType.FISH,    "ANY",                     10,  500,  false));
        FREE_POOL.add(new BpQuestTemplate("kill_creepers",  "Тихий ужас",      "Убить 20 криперов",              BpQuestType.KILL,    "minecraft:creeper",       20,  450,  false));
        FREE_POOL.add(new BpQuestTemplate("kill_spiders",   "Арахнофоб",       "Убить 25 пауков",                BpQuestType.KILL,    "minecraft:spider",        25,  400,  false));
        FREE_POOL.add(new BpQuestTemplate("mine_diamonds",  "Алмазная лихорадка","Добыть 8 алмазов",            BpQuestType.MINE,    "DIAMOND_ORE",             8,   600,  false));
        FREE_POOL.add(new BpQuestTemplate("mine_deepslate", "Глубокий шахтёр", "Добыть 128 глубинного сланца",  BpQuestType.MINE,    "DEEPSLATE",               128, 400,  false));
        FREE_POOL.add(new BpQuestTemplate("kill_pillagers", "Страж порядка",   "Убить 20 разбойников",           BpQuestType.KILL,    "minecraft:pillager",      20,  450,  false));
        FREE_POOL.add(new BpQuestTemplate("collect_wool",   "Овцевод",         "Собрать 32 шерсти",              BpQuestType.COLLECT, "WHITE_WOOL",              32,  350,  false));
        FREE_POOL.add(new BpQuestTemplate("kill_blazes",    "Пожарный",        "Убить 15 блейзов",               BpQuestType.KILL,        "minecraft:blaze",  15,  500,  false));
        FREE_POOL.add(new BpQuestTemplate("market_sell_20","Торговец",         "Продать 20 предметов на рынке",  BpQuestType.MARKET_SELL, "any",              20,  600,  false));
        FREE_POOL.add(new BpQuestTemplate("market_buy_10", "Покупатель",       "Купить 10 предметов на рынке",   BpQuestType.MARKET_BUY,  "any",              10,  500,  false));

        // ── Premium quests ──
        // Modded bosses target L_Ender's Cataclysm (installed). Any target whose mob/block
        // isn't registered on the server is dropped at startup by filterAvailable().
        PREMIUM_POOL.add(new BpQuestTemplate("kill_ignis",        "Гроза Пепла",            "Победить Игниса (Cataclysm)",    BpQuestType.KILL, "cataclysm:ignis",                    1, 1000, true));
        PREMIUM_POOL.add(new BpQuestTemplate("kill_leviathan",    "Морской Титан",          "Победить Левиафана (Cataclysm)", BpQuestType.KILL, "cataclysm:the_leviathan",            1, 1000, true));
        PREMIUM_POOL.add(new BpQuestTemplate("kill_monstrosity",  "Незеритовый Кошмар",     "Победить Незеритовое Чудовище",  BpQuestType.KILL, "cataclysm:netherite_monstrosity",    1, 1000, true));
        PREMIUM_POOL.add(new BpQuestTemplate("kill_wither",       "Победитель Визера",      "Победить Визера",                BpQuestType.KILL, "minecraft:wither",              1, 1000, true));
        PREMIUM_POOL.add(new BpQuestTemplate("kill_elder",        "Убийца Стражника",       "Убить Старшего Стражника",       BpQuestType.KILL, "minecraft:elder_guardian",      1, 1000, true));
        PREMIUM_POOL.add(new BpQuestTemplate("kill_ender_guard",  "Страж Энда",             "Победить Стража Энда (Cataclysm)",BpQuestType.KILL, "cataclysm:ender_guardian",           1, 1000, true));
        PREMIUM_POOL.add(new BpQuestTemplate("mine_debris",       "Охотник за Обломками",   "Добыть 16 ancient debris",       BpQuestType.MINE, "ANCIENT_DEBRIS",               16, 1000, true));
        PREMIUM_POOL.add(new BpQuestTemplate("kill_mobs_100",     "Армагеддон",             "Убить 100 любых мобов",          BpQuestType.KILL, "any",                         100,  900, true));
        PREMIUM_POOL.add(new BpQuestTemplate("kill_nether",       "Завоеватель Ада",        "Убить 20 пигменов",              BpQuestType.KILL, "minecraft:piglin",             20,  800, true));
        PREMIUM_POOL.add(new BpQuestTemplate("kill_endermen",     "Тьма Энда",              "Убить 15 эндерменов",            BpQuestType.KILL, "minecraft:enderman",           15,  800, true));
        PREMIUM_POOL.add(new BpQuestTemplate("mine_emerald",      "Изумрудный путь",        "Добыть 16 изумрудов",            BpQuestType.MINE, "EMERALD_ORE",                  16,  900, true));
        PREMIUM_POOL.add(new BpQuestTemplate("kill_harbinger",    "Падение Предвестника",   "Победить Предвестника (Cataclysm)",BpQuestType.KILL,"cataclysm:the_harbinger",            1, 1000, true));
        PREMIUM_POOL.add(new BpQuestTemplate("kill_maledictus",   "Проклятый Зверь",        "Победить Малдиктуса (Cataclysm)",BpQuestType.KILL, "cataclysm:maledictus",               1, 1000, true));
        PREMIUM_POOL.add(new BpQuestTemplate("fish_premium",      "Мастер Рыбной Ловли",    "Поймать 20 рыб",                 BpQuestType.FISH, "ANY",                          20,  800, true));
        PREMIUM_POOL.add(new BpQuestTemplate("kill_ender_dragon", "Конец Света",            "Убить Дракона Конца",            BpQuestType.KILL, "minecraft:ender_dragon",        1, 1000, true));
    }

    private BpQuestPool() {}

    /**
     * Drops any quest whose target isn't available on THIS server — a KILL target whose
     * entity type isn't registered (e.g. iceandfire / twilightforest / mowziesmobs when the
     * mod isn't installed) or a MINE/COLLECT material that doesn't exist. Call once on enable
     * so quests are always completable; future missing mods are handled automatically.
     */
    public static void filterAvailable(Logger log) {
        int removed = prune(FREE_POOL, log) + prune(PREMIUM_POOL, log);
        if (log != null) {
            log.info("[BattlePass] Quest pool: " + FREE_POOL.size() + " free / " + PREMIUM_POOL.size()
                    + " premium available (" + removed + " dropped for missing content).");
        }
    }

    private static int prune(List<BpQuestTemplate> pool, Logger log) {
        int removed = 0;
        Iterator<BpQuestTemplate> it = pool.iterator();
        while (it.hasNext()) {
            BpQuestTemplate t = it.next();
            if (!targetAvailable(t)) {
                if (log != null) log.warning("[BattlePass] Quest '" + t.getId() + "' dropped — target unavailable: " + t.getTarget());
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    private static boolean targetAvailable(BpQuestTemplate t) {
        String target = t.getTarget();
        if (target == null) return true;
        String tl = target.trim();
        if (tl.isEmpty() || tl.equalsIgnoreCase("any")) return true;   // wildcard quests always fine
        switch (t.getType()) {
            case KILL: {
                // Namespaced entity key ("namespace:id"); default namespace = minecraft.
                NamespacedKey key = tl.contains(":") ? NamespacedKey.fromString(tl.toLowerCase())
                                                     : NamespacedKey.minecraft(tl.toLowerCase());
                if (key == null) return false;
                try {
                    return Registry.ENTITY_TYPE.get(key) != null;
                } catch (Throwable ignored) {
                    return false;
                }
            }
            case MINE:
            case COLLECT:
                return Material.matchMaterial(tl) != null;   // Bukkit material enum / vanilla block
            default:
                return true;   // FISH / MARKET_* have no concrete target
        }
    }

    /**
     * Selects {@code count} templates from the given pool using the given seed.
     * Same seed always produces the same selection (deterministic for all players).
     */
    public static List<BpQuestTemplate> pickFree(long seed, int count) {
        return pick(FREE_POOL, seed, count);
    }

    public static List<BpQuestTemplate> pickPremium(long seed, int count) {
        return pick(PREMIUM_POOL, seed, count);
    }

    private static List<BpQuestTemplate> pick(List<BpQuestTemplate> pool, long seed, int count) {
        List<BpQuestTemplate> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, new Random(seed));
        int take = Math.min(count, copy.size());
        return Collections.unmodifiableList(copy.subList(0, take));
    }

    public static List<BpQuestTemplate> getFreePool() { return Collections.unmodifiableList(FREE_POOL); }
    public static List<BpQuestTemplate> getPremiumPool() { return Collections.unmodifiableList(PREMIUM_POOL); }
}
