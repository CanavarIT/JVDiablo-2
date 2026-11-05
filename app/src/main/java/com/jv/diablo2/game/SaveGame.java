package com.jv.diablo2.game;

import com.jv.diablo2.game.item.Inventory;
import com.jv.diablo2.game.item.Item;
import com.jv.diablo2.game.item.ItemBase;
import com.jv.diablo2.game.item.ItemDb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/** Собственный формат сохранения JVDiablo2 (версия 1). Импорт настоящих .d2s — отдельный этап. */
public final class SaveGame {
    private SaveGame() {
    }

    private static final int MAGIC = 0x4A564432;   // "JVD2"
    private static final int VERSION = 1;

    public static void write(Hero h, OutputStream os) throws IOException {
        DataOutputStream out = new DataOutputStream(os);
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeUTF(h.name);
        PlayerStats s = h.stats;
        out.writeInt(s.cls.ordinal());
        out.writeInt(s.clvl);
        out.writeLong(s.exp);
        out.writeInt(s.str);
        out.writeInt(s.dex);
        out.writeInt(s.vit);
        out.writeInt(s.ene);
        out.writeInt(s.statPoints);
        out.writeInt(s.skillPoints);
        out.writeInt(s.life);
        out.writeInt(s.mana);
        out.writeInt(s.stamina);

        Map<String, Integer> sk = h.skills.all();
        out.writeInt(sk.size());
        for (Map.Entry<String, Integer> e : sk.entrySet()) {
            out.writeUTF(e.getKey());
            out.writeInt(e.getValue());
        }

        out.writeInt(h.quests.quests.size());
        for (QuestLog.Quest q : h.quests.quests) {
            out.writeUTF(q.id);
            out.writeInt(q.state.ordinal());
        }

        Inventory inv = h.inv;
        out.writeInt(inv.gold);
        writeGrid(out, inv.pack);
        writeGrid(out, inv.stash);
        writeGrid(out, inv.cube);
        Map<Inventory.Slot, Item> eq = inv.equippedMap();
        out.writeInt(eq.size());
        for (Map.Entry<Inventory.Slot, Item> e : eq.entrySet()) {
            out.writeInt(e.getKey().ordinal());
            writeBlob(out, e.getValue());
        }
        out.flush();
    }

    public static Hero read(InputStream is, ItemDb db) throws IOException {
        DataInputStream in = new DataInputStream(is);
        if (in.readInt() != MAGIC) throw new IOException("Это не сохранение JVDiablo2");
        int ver = in.readInt();
        if (ver != VERSION) throw new IOException("Неизвестная версия сохранения: " + ver);
        String name = in.readUTF();
        CharClass cls = CharClass.values()[in.readInt()];
        Hero h = new Hero(cls);
        h.name = name;
        PlayerStats s = h.stats;
        s.clvl = in.readInt();
        s.exp = in.readLong();
        s.str = in.readInt();
        s.dex = in.readInt();
        s.vit = in.readInt();
        s.ene = in.readInt();
        s.statPoints = in.readInt();
        s.skillPoints = in.readInt();
        int life = in.readInt(), mana = in.readInt(), stamina = in.readInt();

        int n = in.readInt();
        for (int i = 0; i < n; i++) h.skills.set(in.readUTF(), in.readInt());

        n = in.readInt();
        for (int i = 0; i < n; i++) {
            QuestLog.Quest q = h.quests.get(in.readUTF());
            int st = in.readInt();
            if (q != null && st >= 0 && st < QuestLog.State.values().length) q.state = QuestLog.State.values()[st];
        }

        Inventory inv = h.inv;
        inv.gold = in.readInt();
        readGrid(in, inv.pack, db);
        readGrid(in, inv.stash, db);
        readGrid(in, inv.cube, db);
        n = in.readInt();
        for (int i = 0; i < n; i++) {
            int slot = in.readInt();
            Item it = readBlob(in, db);
            if (it != null && slot >= 0 && slot < Inventory.Slot.values().length) inv.setEquipped(Inventory.Slot.values()[slot], it);
        }
        inv.recompute(s);
        s.life = Math.min(life, s.maxLife());
        s.mana = Math.min(mana, s.maxMana());
        s.stamina = Math.min(stamina, s.maxStamina());
        return h;
    }

    private static void writeGrid(DataOutputStream out, Inventory.Grid g) throws IOException {
        List<Item> items = g.items();
        out.writeInt(items.size());
        for (Item it : items) {
            int[] p = g.posOf(it);
            out.writeInt(p[0]);
            out.writeInt(p[1]);
            writeBlob(out, it);
        }
    }

    private static void readGrid(DataInputStream in, Inventory.Grid g, ItemDb db) throws IOException {
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            int x = in.readInt(), y = in.readInt();
            Item it = readBlob(in, db);
            if (it != null && !g.place(it, x, y)) g.add(it);
        }
    }

    private static void writeBlob(DataOutputStream out, Item it) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(bo);
        d.writeUTF(it.base.code);
        d.writeByte(it.quality.ordinal());
        d.writeInt(it.ilvl);
        d.writeInt(it.reqLevel);
        d.writeInt(it.defense);
        d.writeInt(it.durability);
        d.writeInt(it.maxDurability);
        d.writeInt(it.sockets);
        d.writeInt(it.quantity);
        d.writeBoolean(it.ethereal);
        d.writeUTF(it.prefix);
        d.writeUTF(it.suffix);
        d.writeUTF(it.title);
        d.writeInt(it.mods.size());
        for (Item.Mod m : it.mods) {
            d.writeUTF(m.stat);
            d.writeUTF(m.param);
            d.writeInt(m.value);
        }
        d.flush();
        byte[] b = bo.toByteArray();
        out.writeInt(b.length);
        out.write(b);
    }

    private static Item readBlob(DataInputStream in, ItemDb db) throws IOException {
        int len = in.readInt();
        byte[] b = new byte[len];
        in.readFully(b);
        DataInputStream d = new DataInputStream(new ByteArrayInputStream(b));
        ItemBase base = db == null ? null : db.get(d.readUTF());
        if (base == null) return null;      // база пропала из данных — предмет пропускаем
        Item it = new Item(base);
        int q = d.readByte();
        if (q >= 0 && q < Item.Quality.values().length) it.quality = Item.Quality.values()[q];
        it.ilvl = d.readInt();
        it.reqLevel = d.readInt();
        it.defense = d.readInt();
        it.durability = d.readInt();
        it.maxDurability = d.readInt();
        it.sockets = d.readInt();
        it.quantity = d.readInt();
        it.ethereal = d.readBoolean();
        it.prefix = d.readUTF();
        it.suffix = d.readUTF();
        it.title = d.readUTF();
        int n = d.readInt();
        for (int i = 0; i < n; i++) it.mods.add(new Item.Mod(d.readUTF(), d.readUTF(), d.readInt()));
        return it;
    }
}
