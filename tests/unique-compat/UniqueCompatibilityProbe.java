import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.EnchantApplyListener;
import com.example.voidscape.enchant.UniqueCompatibility;
import com.example.voidscape.enchant.UniqueEnchant;
import com.example.voidscape.item.RelicService;
import com.example.voidscape.item.RelicService.Relic;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;

/** Separate, disposable Paper probe; never packaged into Evergarden. */
public final class UniqueCompatibilityProbe extends JavaPlugin {
    private VoidscapePlugin garden;
    private RelicService relics;
    private int checks;

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this,()->{
            try {
                garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
                check(garden!=null&&garden.isEnabled(),"Evergarden enabled");
                relics=garden.relics();
                run();
                Files.writeString(Path.of("unique-compat-result.txt"),"PASS "+checks+" Paper checks");
            } catch(Throwable error) {
                getLogger().severe("UNIQUE COMPAT FAILED: "+error);
                error.printStackTrace();
                try {Files.writeString(Path.of("unique-compat-result.txt"),"FAIL "+error);}catch(Exception ignored){}
            }
            Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,20L);
        },20L);
    }

    private void check(boolean yes,String message) {
        if(!yes)throw new AssertionError(message);
        checks++;
    }
    private ItemStack add(ItemStack item,UniqueEnchant enchant) {
        ItemStack result=relics.evaluateScrollCraft(relics.createScrollUnique(enchant),item);
        check(result!=null,"apply "+enchant+" to "+item.getType());
        return result;
    }
    private void denied(ItemStack item,UniqueEnchant enchant) {
        check(relics.evaluateScrollCraft(relics.createScrollUnique(enchant),item)==null,
            "deny "+enchant+" on "+item.getType());
    }
    private ItemStack old(ItemStack item,UniqueEnchant... enchantments) {
        ItemMeta meta=item.getItemMeta();
        for(UniqueEnchant enchant:enchantments)
            meta.getPersistentDataContainer().set(new NamespacedKey("evergarden","ue_"+enchant.id()),PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);
        return item;
    }
    private void run() {
        ItemStack sword=add(new ItemStack(Material.NETHERITE_SWORD),UniqueEnchant.ECHO_STRIKE);
        denied(sword,UniqueEnchant.THUNDERLORD);
        sword=add(sword,UniqueEnchant.VAMPIRIC);
        denied(sword,UniqueEnchant.SOUL_HARVEST);
        denied(sword,UniqueEnchant.BLADE_VORTEX);
        ItemStack vortex=add(new ItemStack(Material.NETHERITE_SWORD),UniqueEnchant.BLADE_VORTEX);
        denied(vortex,UniqueEnchant.VAMPIRIC);
        ItemStack battleAxe=add(new ItemStack(Material.NETHERITE_AXE),UniqueEnchant.TITAN_BREACH);
        denied(battleAxe,UniqueEnchant.ADVANCE_TOOL);
        ItemStack toolAxe=add(new ItemStack(Material.NETHERITE_AXE),UniqueEnchant.ADVANCE_TOOL);
        denied(toolAxe,UniqueEnchant.GUILLOTINE);

        ItemStack bow=add(new ItemStack(Material.BOW),UniqueEnchant.COLOSSUS_SLAYER);
        denied(bow,UniqueEnchant.METEOR_ARROW);
        bow=add(bow,UniqueEnchant.KINETIC_GRAPPLE);
        check(EnchantApplyListener.hasUnique(bow,UniqueEnchant.KINETIC_GRAPPLE),"grapple remains compatible");
        ItemStack storm=relics.create(Relic.STORM_BOW,1);
        denied(storm,UniqueEnchant.RICOCHET);
        storm=add(storm,UniqueEnchant.KINETIC_GRAPPLE);
        check(relics.type(storm)==Relic.STORM_BOW,"relic identity retained");
        denied(relics.create(Relic.NOVA_BOW,1),UniqueEnchant.METEOR_ARROW);

        ItemStack pick=add(new ItemStack(Material.NETHERITE_PICKAXE),UniqueEnchant.SEISMIC_SLAM);
        denied(pick,UniqueEnchant.VEIN_SMELTER);
        denied(pick,UniqueEnchant.ADVANCE_TOOL);
        pick=add(pick,UniqueEnchant.TELEPATHY);
        check(UniqueCompatibility.active(pick,UniqueEnchant.TELEPATHY,relics),"telepathy stays active");
        denied(relics.create(Relic.RIFT_PICKAXE,1),UniqueEnchant.SEISMIC_SLAM);
        denied(relics.create(Relic.SMELTER_PICKAXE,1),UniqueEnchant.VEIN_SMELTER);

        ItemStack bound=add(new ItemStack(Material.DIAMOND_HELMET),UniqueEnchant.SOULBOUND);
        check(relics.evaluateScrollCraft(relics.createScrollEternity(),bound)==null,"Soulbound blocks Eternity");
        ItemStack eternal=relics.evaluateScrollCraft(relics.createScrollEternity(),new ItemStack(Material.DIAMOND_HELMET));
        check(eternal!=null,"Eternity applies to ordinary helmet");
        denied(eternal,UniqueEnchant.SOULBOUND);

        ItemStack legacy=old(new ItemStack(Material.NETHERITE_SWORD),UniqueEnchant.GUILLOTINE,
            UniqueEnchant.THUNDERLORD,UniqueEnchant.VAMPIRIC);
        check(UniqueCompatibility.needsResolution(legacy,relics),"legacy sword requires choice");
        check(UniqueCompatibility.active(legacy,UniqueEnchant.GUILLOTINE,relics),"first old attack active");
        check(!UniqueCompatibility.active(legacy,UniqueEnchant.THUNDERLORD,relics),"second old attack dormant");
        check(UniqueCompatibility.active(legacy,UniqueEnchant.VAMPIRIC,relics),"compatible sustain active");
        var chosen=UniqueCompatibility.resolve(legacy,"thunderlord",relics);
        check(chosen!=null&&chosen.refunds().size()==1,"one attack scroll refunded");
        check(relics.getUniqueEnchant(chosen.refunds().getFirst())==UniqueEnchant.GUILLOTINE,"correct attack scroll refunded");
        check(!UniqueCompatibility.needsResolution(chosen.item(),relics),"chosen sword fully resolved");
        check(UniqueCompatibility.active(chosen.item(),UniqueEnchant.THUNDERLORD,relics),"chosen attack becomes active");

        ItemStack oldEternal=old(new ItemStack(Material.DIAMOND_HELMET),UniqueEnchant.SOULBOUND);
        ItemMeta eternalMeta=oldEternal.getItemMeta();relics.applyEternityMeta(eternalMeta);oldEternal.setItemMeta(eternalMeta);
        check(!UniqueCompatibility.active(oldEternal,UniqueEnchant.SOULBOUND,relics),"old Soulbound dormant under Eternity");
        var keepBound=UniqueCompatibility.resolve(oldEternal,"soulbound",relics);
        check(keepBound!=null&&keepBound.refunds().size()==1&&!relics.isEternityItem(keepBound.item()),"keeping Soulbound refunds Eternity");
        check(relics.isScrollEternity(keepBound.refunds().getFirst()),"Eternity scroll returned");
        var keepEternal=UniqueCompatibility.resolve(oldEternal,"eternity",relics);
        check(keepEternal!=null&&keepEternal.refunds().size()==1,"keeping Eternity refunds Soulbound");
        check(relics.getUniqueEnchant(keepEternal.refunds().getFirst())==UniqueEnchant.SOULBOUND,"Soulbound scroll returned");

        ItemStack oldStorm=old(relics.create(Relic.STORM_BOW,1),UniqueEnchant.RICOCHET);
        check(!UniqueCompatibility.active(oldStorm,UniqueEnchant.RICOCHET,relics),"old relic conflict dormant");
        var keepRelic=UniqueCompatibility.resolve(oldStorm,"relic",relics);
        check(keepRelic!=null&&keepRelic.refunds().size()==1&&!UniqueCompatibility.needsResolution(keepRelic.item(),relics),"relic signature kept and scroll refunded");

        ItemStack oldPick=old(new ItemStack(Material.NETHERITE_PICKAXE),UniqueEnchant.SEISMIC_SLAM,UniqueEnchant.ADVANCE_TOOL);
        ItemMeta toolMeta=oldPick.getItemMeta();EnchantApplyListener.applyAdvanceToolComponent(toolMeta);oldPick.setItemMeta(toolMeta);
        check(oldPick.getItemMeta().hasTool(),"old Advance Tool component exists");
        relics.migrate(oldPick);
        check(!oldPick.getItemMeta().hasTool(),"dormant Advance Tool component removed");
        var keepAdvance=UniqueCompatibility.resolve(oldPick,"advance_tool",relics);
        check(keepAdvance!=null&&keepAdvance.refunds().size()==1,"Advance Tool can be chosen");
        ItemStack resolvedPick=keepAdvance.item();relics.migrate(resolvedPick);
        check(resolvedPick.getItemMeta().hasTool(),"chosen Advance Tool component restored");
        getLogger().info("PASS "+checks+" unique compatibility checks");
    }
}
