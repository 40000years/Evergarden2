import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.item.RetiredRecipeData;
import com.example.advancemagic.AdvanceMagicPlugin;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;

final class RecipeMenuChecks {
    static void files(VoidscapePlugin garden) throws Exception {
        Path base=Path.of("bedrock_test/playerdata");byte[] expected=Files.readAllBytes(Path.of("recipe-fixture-expected.nbt"));
        for(String suffix:List.of(".dat",".dat_old")){
            Path file=base.resolve("12345678-1234-1234-1234-123456789abc"+suffix);byte[] actual;
            try(var input=new GZIPInputStream(Files.newInputStream(file))){actual=input.readAllBytes();}
            if(!Arrays.equals(expected,actual))throw new AssertionError("Recipe startup migration must preserve unrelated NBT exactly "+suffix);
            if(!Arrays.equals(actual,RetiredRecipeData.cleanNbt(actual)))throw new AssertionError("Migration must be idempotent");
        }
        Path backups=garden.getDataFolder().toPath().resolve("retired-recipe-backups/bedrock_test");
        try(var files=Files.list(backups)){if(files.count()!=2)throw new AssertionError("Original primary and fallback data must be backed up");}
        try{RetiredRecipeData.cleanNbt(new byte[]{10,0,0,9});throw new AssertionError("Malformed NBT accepted");}catch(IOException expectedError){}
        garden.getLogger().info("PASS pre-login recipe cleanup: both files, exact unrelated NBT, backups, idempotence and malformed input");
    }
    static void menu(VoidscapePlugin garden,AdvanceMagicPlugin magic,Player player){
        player.setOp(true);player.getInventory().clear();garden.testGui().open(player);
        if(player.getOpenInventory().getTopInventory().getItem(2)==null)throw new AssertionError("Admin Test menu lacks potion button");
        garden.testGui().openManaPotions(player);
        var inventory=player.getOpenInventory().getTopInventory();
        for(var quality:com.example.advancemagic.mana.ManaPotionService.Quality.values()){
            int slot=10+quality.ordinal()*2;
            if(magic.manaPotions().quality(inventory.getItem(slot))!=quality)throw new AssertionError("Wrong menu quality");
            for(var click:List.of(ClickType.LEFT,ClickType.RIGHT,ClickType.SHIFT_LEFT)){
                player.getInventory().clear();
                var event=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,click,InventoryAction.PICKUP_ALL);
                garden.testGui().onInventoryClick(event);
                int count=0;
                for(var item:player.getInventory().getContents())if(item!=null){
                    if(magic.manaPotions().quality(item)!=quality||item.getAmount()!=1)throw new AssertionError("Wrong issued quality or illegal potion stack");count++;
                }
                if(!event.isCancelled()||count!=(click==ClickType.LEFT?1:click==ClickType.RIGHT?4:8))throw new AssertionError("Wrong menu issue count "+click+" "+count);
            }
        }
        player.setOp(false);player.getInventory().clear();
        var denied=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,10,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        garden.testGui().onInventoryClick(denied);
        if(!denied.isCancelled()||!player.getInventory().isEmpty())throw new AssertionError("Nonadmin can use potion menu");
        player.setOp(true);garden.getLogger().info("PASS mana admin menu: 4 qualities, left/right/shift quantities, unstacked bottles and permission denial");
    }
}
