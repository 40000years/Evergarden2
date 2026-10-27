import com.example.advancemagic.elemental.RetiredStaffRecipeData;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Independently authored player NBT fixtures: only the two recipe-book lists may change. */
public final class RetiredStaffRecipeChecks {
    static final String UUID="79ac9a89-902d-43e7-a88a-aed7f494cdf0";
    static final String[] ELEMENTS={"fire","wind","earth","ice","water","lightning","light","dark"};
    static int checks;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static void text(DataOutputStream out,String name,String value)throws IOException{out.writeByte(8);out.writeUTF(name);out.writeUTF(value);}
    static void list(DataOutputStream out,String name,List<String> values)throws IOException{
        out.writeByte(9);out.writeUTF(name);out.writeByte(8);out.writeInt(values.size());for(String value:values)out.writeUTF(value);
    }
    public static byte[] fixture(boolean includeRetired)throws IOException{
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeByte(10);out.writeUTF("");
        out.writeByte(3);out.writeUTF("DataVersion");out.writeInt(5000);
        text(out,"CustomName","ผู้เล่นทดสอบ — keep UTF-8");
        text(out,"unrelatedRecipe","advance-magic:elemental_staff_fire");
        out.writeByte(10);out.writeUTF("recipeBook");
        out.writeByte(1);out.writeUTF("isGuiOpen");out.writeBoolean(true);
        out.writeByte(1);out.writeUTF("isFilteringCraftable");out.writeBoolean(false);
        var recipes=new ArrayList<>(List.of("minecraft:crafting_table","advance-magic:wooden_staff","advance-magic:elemental_staff_future","other_plugin:elemental_staff_fire"));
        if(includeRetired)for(String element:ELEMENTS)recipes.add("advance-magic:elemental_staff_"+element);
        list(out,"recipes",recipes);
        var pending=new ArrayList<>(List.of("minecraft:stick","advance-magic:wooden_staff"));
        if(includeRetired)for(String element:ELEMENTS)pending.add("advance-magic:elemental_staff_"+element);
        list(out,"toBeDisplayed",pending);out.writeByte(0);
        // Matching strings in inventory/custom data and nested recipeBook must survive verbatim.
        out.writeByte(9);out.writeUTF("Inventory");out.writeByte(10);out.writeInt(1);
        out.writeByte(1);out.writeUTF("Slot");out.writeByte(7);
        text(out,"id","minecraft:paper");out.writeByte(3);out.writeUTF("count");out.writeInt(64);
        out.writeByte(10);out.writeUTF("components");
        text(out,"minecraft:custom_name","advance-magic:elemental_staff_fire");
        out.writeByte(10);out.writeUTF("recipeBook");list(out,"recipes",List.of("advance-magic:elemental_staff_fire"));out.writeByte(0);
        out.writeByte(0);out.writeByte(0);
        out.writeByte(11);out.writeUTF("UUID");out.writeInt(4);for(int i:new int[]{1,-2,3,4})out.writeInt(i);
        out.writeByte(7);out.writeUTF("opaqueBytes");out.writeInt(5);out.write(new byte[]{0,1,-1,7,0});
        out.writeByte(0);return bytes.toByteArray();
    }
    interface Checked{void run()throws IOException;}
    static void rejects(Checked task,String message)throws IOException{
        boolean rejected=false;try{task.run();}catch(IOException expected){rejected=true;}check(rejected,message);
    }
    public static void main(String[] args)throws Exception{
        byte[] original=fixture(true),expected=fixture(false);
        check(Arrays.equals(RetiredStaffRecipeData.cleanNbt(original),expected),"exact eight recipe IDs removed from both lists; every other byte preserved");
        check(Arrays.equals(RetiredStaffRecipeData.cleanNbt(expected),expected),"clean player data is idempotent");
        rejects(()->RetiredStaffRecipeData.cleanNbt(new byte[]{1,0,0,0}),"non-compound root rejected");
        rejects(()->RetiredStaffRecipeData.cleanNbt(Arrays.copyOf(original,original.length-1)),"truncated player data rejected");
        rejects(()->RetiredStaffRecipeData.cleanNbt(Arrays.copyOf(original,original.length+1)),"trailing bytes rejected");
        if(args.length==1){
            Path folder=Path.of(args[0]);Files.createDirectories(folder);
            try(var gzip=new GZIPOutputStream(Files.newOutputStream(folder.resolve(UUID+".dat")))){gzip.write(original);}
            Files.write(folder.resolve("expected.nbt"),expected);
        }
        System.out.println("PASS "+checks+" retired staff recipe NBT checks");
    }
}
