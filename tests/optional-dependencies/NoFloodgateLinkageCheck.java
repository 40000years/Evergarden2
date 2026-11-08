import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/** Model Paper's listener-method scan with all Floodgate/Cumulus classes unavailable. */
public final class NoFloodgateLinkageCheck {
    public static void main(String[] args) throws Exception {
        URL[] urls=new URL[args.length];
        for(int i=0;i<args.length;i++)urls[i]=Path.of(args[i]).toUri().toURL();
        try(var loader=new URLClassLoader(urls,ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(name.startsWith("org.geysermc.cumulus.")||name.startsWith("org.geysermc.floodgate."))
                    throw new ClassNotFoundException("optional API absent: "+name);
                return super.loadClass(name,resolve);
            }
        }) {
            for(String name:new String[]{"com.example.voidscape.pet.PetMenu","com.example.voidscape.trade.SeraphShop"}) {
                Class<?> type=Class.forName(name,false,loader);
                System.out.println(name+": "+type.getDeclaredMethods().length+" methods, "+type.getDeclaredConstructors().length+" constructors");
            }
        }
        System.out.println("PASS: listener method scans without Floodgate or Cumulus");
    }
}
