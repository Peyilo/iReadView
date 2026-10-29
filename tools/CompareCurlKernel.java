import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Independent class loaders compare a saved pre-refactor JAR with the candidate.
 * Run with JDK 17; this tool is never packaged in the Kotlin/Android libraries.
 * Vertex padding is excluded, exactly as in the original mesh golden fixtures.
 */
public final class CompareCurlKernel {
    static final String ROOT = "org.peyilo.ioscurl.";
    static final long SEED = 0xC01220261029L;
    record Case(double time, double angle, double radius, double width, double height,
                double scale, double start, double end, boolean front, boolean back,
                double[][] colors, double[][] rects, double softness) {}

    static final class Kernel implements AutoCloseable {
        final URLClassLoader loader;
        final Object mesh;
        final Constructor<?> values, color, material, rect, input;
        final Method build;
        Object lastFrame;
        Kernel(Path jar, Path stdlib) throws Exception {
            loader = new URLClassLoader(new URL[] {jar.toUri().toURL(), stdlib.toUri().toURL()},
                    ClassLoader.getPlatformClassLoader());
            Class<?> meshClass = type("mesh.CurlMesh");
            mesh = meshClass.getConstructor().newInstance();
            Class<?> valueType = type("model.CurlValues"), colorType = type("mesh.CurlColor"),
                    materialType = type("mesh.CurlMaterial"), rectType = type("model.CurlRect"),
                    inputType = type("mesh.CurlMeshInput");
            values = valueType.getConstructor(double.class, double.class, double.class);
            color = colorType.getConstructor(double.class, double.class, double.class, double.class);
            material = materialType.getConstructor(colorType, colorType, colorType, colorType);
            rect = rectType.getConstructor(double.class, double.class, double.class, double.class);
            input = inputType.getConstructor(valueType, double.class, double.class, double.class,
                    double.class, double.class, boolean.class, boolean.class, materialType,
                    rectType, rectType, rectType, rectType, double.class);
            build = meshClass.getMethod("build", inputType);
        }
        Class<?> type(String name) throws Exception { return loader.loadClass(ROOT + name); }
        Object rectangle(double[] v) throws Exception {
            return v == null ? null : rect.newInstance(v[0], v[1], v[2], v[3]);
        }
        String evaluate(Case c) throws Exception {
            Object[] colors = new Object[4];
            for (int i=0;i<4;i++) colors[i] = color.newInstance(c.colors[i][0], c.colors[i][1], c.colors[i][2], c.colors[i][3]);
            Object in = input.newInstance(values.newInstance(c.time,c.radius,c.angle), c.width,c.height,c.scale,
                    c.start,c.end,c.front,c.back,material.newInstance(colors),rectangle(c.rects[0]),
                    rectangle(c.rects[1]),rectangle(c.rects[2]),rectangle(c.rects[3]),c.softness);
            Object frame;
            lastFrame = null;
            try { frame = build.invoke(mesh, in); }
            catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                return "error:" + cause.getClass().getName() + ":" + cause.getMessage();
            }
            lastFrame = frame;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt((int)get(frame,"Width")); out.writeInt((int)get(frame,"Height"));
            List<?> batches = (List<?>)get(frame,"Batches"); out.writeInt(batches.size());
            for (Object batch : batches) {
                for (String field : List.of("Primitive","Mode","BlendFunction","VertexCount","Count"))
                    out.writeInt((int)get(batch,field));
                out.writeBoolean((boolean)get(batch,"BlendEnabled"));
                Object clip = get(batch,"Clip");
                for (String field : List.of("X","Y","Width","Height")) out.writeLong(Double.doubleToRawLongBits((double)get(clip,field)));
                byte[] vertices = (byte[])get(batch,"Vertices"); out.writeInt(vertices.length);
                for (int offset=0;offset<vertices.length;offset+=64) {
                    out.write(vertices,offset,24); out.write(vertices,offset+40,8);
                }
                short[] indices = (short[])get(batch,"Indices");
                out.writeInt(indices == null ? -1 : indices.length);
                if (indices != null) for (short index : indices) out.writeShort(index);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }
        public void close() throws IOException { loader.close(); }
        String difference(Kernel other) throws Exception {
            if (lastFrame == null || other.lastFrame == null) return "no comparable frame";
            List<?> a=(List<?>)get(lastFrame,"Batches"), b=(List<?>)get(other.lastFrame,"Batches");
            for (int batch=0;batch<Math.min(a.size(),b.size());batch++) {
                byte[] x=(byte[])get(a.get(batch),"Vertices"), y=(byte[])get(b.get(batch),"Vertices");
                for (int vertex=0;vertex+64<=Math.min(x.length,y.length);vertex+=64) {
                    for (int offset : new int[]{0,4,8,12,16,20,40,44}) {
                        int before=java.nio.ByteBuffer.wrap(x,vertex+offset,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
                        int after=java.nio.ByteBuffer.wrap(y,vertex+offset,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
                        if (before!=after) return "batch="+batch+" vertex="+(vertex/64)+" attributeByte="+offset+
                                " before="+Float.intBitsToFloat(before)+" ("+Integer.toHexString(before)+")"+
                                " after="+Float.intBitsToFloat(after)+" ("+Integer.toHexString(after)+")";
                    }
                }
            }
            return "batch metadata, buffer length or indices differ";
        }
    }
    static Object get(Object value, String field) throws Exception {
        return value.getClass().getMethod("get"+field).invoke(value);
    }
    static Case next(Random r, int index) {
        double w=80+r.nextInt(721), h=100+r.nextInt(1001), scale=.1+r.nextDouble()*3.9;
        double angle = switch(index%8) {
            case 0 -> 0; case 1 -> Math.PI/2; case 2 -> -Math.PI/2;
            default -> (r.nextDouble()*2-1)*Math.PI;
        };
        double[][] colors = new double[4][4];
        for (double[] color:colors) {
            for (int i=0;i<3;i++) color[i]=r.nextDouble();
            color[3] = index%5==0 ? 0 : index%5==1 ? 1 : r.nextDouble();
        }
        double pw=Math.ceil(w*scale), ph=Math.ceil(h*scale);
        double[][] rects = new double[4][];
        if (index%3==0) rects[0]=new double[]{r.nextInt(31)-15,r.nextInt(31)-15,pw,ph};
        if (index%4==0) rects[1]=new double[]{-7,13,pw,ph};
        if (index%5==0) rects[2]=new double[]{3,17,pw*.75,index%10==0 ? 0 : ph*.8};
        if (index%7==0) rects[3]=new double[]{1,1,w*.9,h*.85};
        double start=index%6==0 ? .15 : 0, end=index%6==0 ? Math.PI-.2 : Math.PI;
        return new Case(r.nextDouble(),angle,8+r.nextDouble()*Math.min(w,h),w,h,scale,start,end,
                index%4!=0,index%4!=1,colors,rects,1+r.nextDouble()*2);
    }
    static Case atShadowBoundary(Case c, int index) {
        double lower=(double).4f/(double)1.35f, upper=1.0/(double)1.35f;
        double[] times={0,Math.nextUp(0.0),Math.nextDown(lower),lower,Math.nextUp(lower),.5,
                Math.nextDown(upper),upper,Math.nextUp(upper),Math.nextDown(1.0),1};
        return new Case(times[index%times.length],c.angle,c.radius,c.width,c.height,c.scale,
                c.start,c.end,c.front,c.back,c.colors,c.rects,c.softness);
    }
    static Case atProjectionBoundary(Case c, int index) {
        double snap=Math.asin((double).001f/c.scale);
        double[] angles={0.0,-0.0,Math.nextUp(0.0),Math.nextDown(0.0),
                Math.PI/2,-Math.PI/2,Math.PI,-Math.PI,
                Math.nextUp(Math.PI/2),Math.nextDown(Math.PI/2),
                Math.nextUp(-Math.PI/2),Math.nextDown(-Math.PI/2),
                snap,Math.nextUp(snap),Math.nextDown(snap),-snap,
                (double)Math.nextUp((float)snap),(double)Math.nextDown((float)snap)};
        double[] radii={.01,1,8,4096,c.radius};
        double[][] intervals={{0,Math.PI},{.15,Math.PI-.2},{Math.PI/2,Math.PI/2},
                {-Math.PI/4,Math.PI*1.2},{1.7,1.2}};
        double[] interval=intervals[(index/angles.length)%intervals.length];
        return new Case(c.time,angles[index%angles.length],radii[index%radii.length],
                c.width,c.height,c.scale,interval[0],interval[1],c.front,c.back,c.colors,c.rects,c.softness);
    }
    public static void main(String[] args) throws Exception {
        if (args.length<3) throw new IllegalArgumentException("reference.jar candidate.jar kotlin-stdlib.jar [cases] [boundaries|projection]");
        int total=args.length>3 ? Integer.parseInt(args[3]) : 1024;
        String distribution=args.length>4 ? args[4] : "random";
        boolean boundaries=distribution.equals("boundaries");
        int successful=0, rejected=0;
        Random random=new Random(SEED);
        try (Kernel reference=new Kernel(Path.of(args[0]),Path.of(args[2]));
             Kernel candidate=new Kernel(Path.of(args[1]),Path.of(args[2]))) {
            for (int i=0;i<total;i++) {
                Case c=next(random,i);
                if (boundaries) c=atShadowBoundary(c,i);
                if (distribution.equals("projection")) c=atProjectionBoundary(c,i);
                String before=reference.evaluate(c), after=candidate.evaluate(c);
                if (!before.equals(after)) throw new AssertionError("case="+i+" input="+c+" "+reference.difference(candidate)+" reference="+before+" candidate="+after);
                if (before.startsWith("error:")) rejected++; else successful++;
            }
        }
        System.out.println("{\"cases\":"+total+",\"matchedFrames\":"+successful+",\"matchedRejections\":"+rejected+",\"mismatches\":0,\"seed\":"+SEED+",\"distribution\":\""+(boundaries ? "shadow-boundaries" : distribution)+"\"}");
    }
}
