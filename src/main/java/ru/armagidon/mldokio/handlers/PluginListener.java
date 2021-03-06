package ru.armagidon.mldokio.handlers;

import com.comphenix.protocol.ProtocolLibrary;
import org.bukkit.*;
import org.bukkit.block.Jukebox;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.armagidon.mldokio.MLDokio;
import ru.armagidon.mldokio.events.JukeboxSongPlayEvent;
import ru.armagidon.mldokio.events.JukeboxSongStopEvent;
import ru.armagidon.mldokio.jukebox.JukeBox;
import ru.armagidon.mldokio.player.MusicListener;
import ru.armagidon.mldokio.recorder.Recordings;
import ru.armagidon.mldokio.sound.SoundTrack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PluginListener implements Listener
{
    private final Recordings recordings;
    private final Map<Location, UUID> jukeBoxIds = new HashMap<>();


    public PluginListener(Recordings recordings) {
        Bukkit.getPluginManager().registerEvents(this, MLDokio.getInstance());

        ProtocolLibrary.getProtocolManager().addPacketListener(new JukeboxPacketHandler());

        Bukkit.getOnlinePlayers().forEach(MusicListener::addMusicListener);
        this.recordings = recordings;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event){
        MusicListener.addMusicListener(event.getPlayer());
    }

    @EventHandler
    public void onEvent(PlayerQuitEvent event){
        MusicListener.musicListeners.remove(event.getPlayer());
    }

    @EventHandler
    public void onPlaySong(JukeboxSongPlayEvent event){
        ItemStack disc = event.getDisc();
        ItemMeta meta = disc.getItemMeta();
        NamespacedKey tagKey = new NamespacedKey(MLDokio.getInstance(), "DiscData");
        if(meta.getPersistentDataContainer().has(tagKey, PersistentDataType.STRING)){
            String uuidString = meta.getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
            if(uuidString==null) return;
            UUID uuid = UUID.fromString(uuidString);
            SoundTrack trackToPlay = recordings.getTrack(uuid);
            if(trackToPlay==null) return;

            UUID id = MLDokio.getInstance().getJukeBoxPool().dedicateJukeBox(event.getJukebox().getLocation(), trackToPlay.getBuffer(), false);
            jukeBoxIds.put(event.getJukebox().getLocation(), id);

            JukeBox jukeBox = MLDokio.getInstance().getJukeBoxPool().getJukeBoxByIdOrNullIfNotFound(id);

            jukeBox.play();
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onStopSong(JukeboxSongStopEvent event){
        Location location  = event.getJukebox().getLocation();
        UUID id = jukeBoxIds.get(location);
        JukeBox jukeBox = MLDokio.getInstance().getJukeBoxPool().getJukeBoxByIdOrNullIfNotFound(id);
        jukeBox.stop();
        jukeBoxIds.remove(location);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event){
        if(event.getBlock().getType().equals(Material.JUKEBOX)){
            Jukebox jukebox = (Jukebox) event.getBlock().getState();
            if(jukeBoxIds.containsKey(jukebox.getLocation()))
                new JukeboxSongStopEvent(jukebox, jukebox.getRecord()).callEvent();
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onRename(InventoryClickEvent event){
        InventoryType.SlotType slotType = event.getSlotType();
        if(!slotType.equals(InventoryType.SlotType.RESULT)) return;
        if(!event.getInventory().getType().equals(InventoryType.ANVIL)) return;
        ItemStack item = event.getCurrentItem();
        if(item==null||!item.getType().isRecord()) return;

        ItemMeta meta = item.getItemMeta();
        NamespacedKey tagKey = new NamespacedKey(MLDokio.getInstance(), "DiscData");
        String uuidString = meta.getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
        if(uuidString==null) return;
        UUID uuid = UUID.fromString(uuidString);
        SoundTrack trackToPlay = recordings.getTrack(uuid);
        if(trackToPlay==null) return;

        UUID playerId = event.getWhoClicked().getUniqueId();
        if(!playerId.equals(trackToPlay.getAuthorId())) return;
        recordings.changeLabel(trackToPlay, item.getItemMeta().getDisplayName());
        meta.setLore(Collections.singletonList(String.valueOf(ChatColor.ITALIC) + ChatColor.GRAY + trackToPlay.getLabel()));
        item.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDiscUpdate(InventoryClickEvent event){
        ItemStack item = event.getCurrentItem();
        if(item==null||!item.getType().isRecord()) return;

        ItemMeta meta = item.getItemMeta();
        NamespacedKey tagKey = new NamespacedKey(MLDokio.getInstance(), "DiscData");
        String uuidString = meta.getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
        if(uuidString==null) return;
        UUID uuid = UUID.fromString(uuidString);
        SoundTrack trackToPlay = recordings.getTrack(uuid);
        if(trackToPlay==null) return;

        meta.setLore(Collections.singletonList(String.valueOf(ChatColor.ITALIC) + ChatColor.GRAY + trackToPlay.getLabel()));
        item.setItemMeta(meta);
    }

}
