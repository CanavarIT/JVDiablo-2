package com.jv.diablo2.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Журнал заданий всех пяти актов. Пока завершаются убийством босса; диалоги с NPC и предметные
 * условия появятся вместе с картами и NPC.
 */
public final class QuestLog {
    public enum State { NOT_STARTED, ACTIVE, DONE }

    public static final class Quest {
        public final int act;
        public final String id, name;
        public final String[] killIds;
        public State state = State.NOT_STARTED;

        Quest(int act, String id, String name, String... killIds) {
            this.act = act;
            this.id = id;
            this.name = name;
            this.killIds = killIds;
        }
    }

    public final List<Quest> quests = new ArrayList<>();

    public QuestLog() {
        a(1, "den", "Den of Evil");
        a(1, "raven", "Sisters' Burial Grounds", "bloodraven");
        a(1, "tools", "Tools of the Trade");
        a(1, "cain", "The Search for Cain");
        a(1, "tower", "The Forgotten Tower", "countess");
        a(1, "andariel", "Sisters to the Slaughter", "andariel");
        a(2, "radament", "Radament's Lair", "radament");
        a(2, "staff", "The Horadric Staff");
        a(2, "sun", "Tainted Sun");
        a(2, "arcane", "Arcane Sanctuary");
        a(2, "summoner", "The Summoner", "summoner");
        a(2, "duriel", "The Seven Tombs", "duriel");
        a(3, "tome", "Lam Esen's Tome");
        a(3, "khalim", "Khalim's Will");
        a(3, "blade", "Blade of the Old Religion");
        a(3, "bird", "The Golden Bird");
        a(3, "temple", "The Blackened Temple");
        a(3, "mephisto", "The Guardian", "mephisto");
        a(4, "izual", "The Fallen Angel", "izual");
        a(4, "hellforge", "Hell's Forge");
        a(4, "diablo", "Terror's End", "diablo");
        a(5, "siege", "Siege on Harrogath");
        a(5, "rescue", "Rescue on Mount Arreat");
        a(5, "ice", "Prison of Ice");
        a(5, "betrayal", "Betrayal of Harrogath", "nihlathak");
        a(5, "ancients", "Rite of Passage");
        a(5, "baal", "Eve of Destruction", "baalcrab", "baal");
    }

    private void a(int act, String id, String name, String... kills) {
        quests.add(new Quest(act, id, name, kills));
    }

    public Quest get(String id) {
        for (Quest q : quests) if (q.id.equals(id)) return q;
        return null;
    }

    /** Возвращает задание, завершившееся этим убийством, или null. */
    public Quest onKill(String monsterId) {
        String m = monsterId.toLowerCase(Locale.ROOT);
        for (Quest q : quests) {
            if (q.state == State.DONE) continue;
            for (String k : q.killIds) {
                if (m.equals(k)) {
                    q.state = State.DONE;
                    return q;
                }
            }
        }
        return null;
    }
}
