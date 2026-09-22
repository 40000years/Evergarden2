package com.example.advancemagic.effect;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class Geometry {
    private Geometry(){}
    public static boolean intersects(BoundingBox box,Vector from,Vector to) {
        if(box.contains(from)||box.contains(to))return true;
        Vector delta=to.clone().subtract(from);double length=delta.length();
        return length>1e-9&&box.rayTrace(from,delta,length)!=null;
    }
}
