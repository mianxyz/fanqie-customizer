package io.github.mianxyz.fanqiecustomizer;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Reverses only reviewed symbol-boundary substitutions; original renderer hashes stay pinned. */
public final class HostBaselineEquivalenceTest {
 static byte[] originalBytes(String file)throws Exception{
  String s=Files.readString(Path.of("src/io/github/mianxyz/fanqiecustomizer",file));
  for(String row:Files.readAllLines(Path.of("tests/fixtures/Visual-600-inverse.tsv"))){String[] p=row.split("\t",-1);if(!p[0].equals(file))continue;String current=new String(Base64.getDecoder().decode(p[2]),StandardCharsets.UTF_8);if(!s.equals(current))throw new AssertionError("unreviewed visual patch: "+file);s=new String(Base64.getDecoder().decode(p[1]),StandardCharsets.UTF_8);}
  for(String row:Files.readAllLines(Path.of("tests/fixtures/RankRepair-600-inverse.tsv"))){String[] p=row.split(String.valueOf((char)9),-1);if(!p[0].equals(file))continue;String current=new String(Base64.getDecoder().decode(p[2]),StandardCharsets.UTF_8);if(!s.equals(current))throw new AssertionError("unreviewed rank repair patch: "+file);s=new String(Base64.getDecoder().decode(p[1]),StandardCharsets.UTF_8);}
  for(String row:Files.readAllLines(Path.of("tests/fixtures/Mine-600-inverse.tsv"))){String[] p=row.split("\t",-1);if(!p[0].equals(file))continue;String current=new String(Base64.getDecoder().decode(p[2]),StandardCharsets.UTF_8);if(!s.equals(current))throw new AssertionError("unreviewed mine patch: "+file);s=new String(Base64.getDecoder().decode(p[1]),StandardCharsets.UTF_8);}
  for(String row:Files.readAllLines(Path.of("tests/fixtures/BarPress-600-inverse.tsv"))){String[] p=row.split("\t",-1);if(!p[0].equals(file))continue;String current=new String(Base64.getDecoder().decode(p[2]),StandardCharsets.UTF_8);if(!s.equals(current))throw new AssertionError("unreviewed bar press patch: "+file);s=new String(Base64.getDecoder().decode(p[1]),StandardCharsets.UTF_8);}
  for(String row:Files.readAllLines(Path.of("tests/fixtures/Close-600-inverse.tsv"))){String[] p=row.split("\t",-1);if(!p[0].equals(file))continue;String current=new String(Base64.getDecoder().decode(p[2]),StandardCharsets.UTF_8);if(!s.equals(current))throw new AssertionError("unreviewed closing patch: "+file);s=new String(Base64.getDecoder().decode(p[1]),StandardCharsets.UTF_8);}
  for(String row:Files.readAllLines(Path.of("tests/fixtures/Performance-600-inverse.tsv"))){String[] p=row.split("\t",-1);if(!p[0].equals(file))continue;String current=new String(Base64.getDecoder().decode(p[2]),StandardCharsets.UTF_8);if(!s.equals(current))throw new AssertionError("unreviewed performance patch: "+file);s=new String(Base64.getDecoder().decode(p[1]),StandardCharsets.UTF_8);}
  for(String row:Files.readAllLines(Path.of("tests/fixtures/RankLayout-600-inverse.tsv"))){String[] p=row.split("\t",-1);if(!p[0].equals(file))continue;String current=new String(Base64.getDecoder().decode(p[2]),StandardCharsets.UTF_8);if(!s.equals(current))throw new AssertionError("unreviewed rank patch: "+file);s=new String(Base64.getDecoder().decode(p[1]),StandardCharsets.UTF_8);}
  for(String row:Files.readAllLines(Path.of("tests/fixtures/GlassBars-600-inverse.tsv"))){String[] p=row.split("	",-1);if(!p[0].equals(file))continue;String current=new String(Base64.getDecoder().decode(p[2]),StandardCharsets.UTF_8);if(!s.equals(current))throw new AssertionError("unreviewed bars patch: "+file);s=new String(Base64.getDecoder().decode(p[1]),StandardCharsets.UTF_8);}
  List<String> rows=Files.readAllLines(Path.of("tests/fixtures/HostSymbols-569-frozen.tsv"));
  Collections.reverse(rows);
  for(String row:rows){String[] p=row.split("\t",-1);if(!p[0].equals(file))continue;
   String old=new String(Base64.getDecoder().decode(p[1]),StandardCharsets.UTF_8),value=new String(Base64.getDecoder().decode(p[2]),StandardCharsets.UTF_8);
   if(!s.contains(value))throw new AssertionError("missing reviewed substitution: "+file+" "+value);
   s=s.replace(value,old);
  }
  byte[] bytes=s.getBytes(StandardCharsets.UTF_8);
  List<String[]> edits=new ArrayList<>();
  for(String row:Files.readAllLines(Path.of("tests/fixtures/HostSymbols-569-edits.tsv"))){String[] p=row.split("\t",-1);if(p[0].equals(file))edits.add(p);}
  edits.sort((a,b)->Integer.compare(Integer.parseInt(b[1]),Integer.parseInt(a[1])));
  for(String[] p:edits){int at=Integer.parseInt(p[1]);byte[] old=Base64.getDecoder().decode(p[2]),value=Base64.getDecoder().decode(p[3]);
   if(at+value.length>bytes.length||!Arrays.equals(Arrays.copyOfRange(bytes,at,at+value.length),value))throw new AssertionError("unreviewed source change: "+file+"@"+at);
   byte[] next=new byte[bytes.length-value.length+old.length];System.arraycopy(bytes,0,next,0,at);System.arraycopy(old,0,next,at,old.length);System.arraycopy(bytes,at+value.length,next,at+old.length,bytes.length-at-value.length);bytes=next;
  }
  return bytes;
 }
 static String originalHash(String file)throws Exception{return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(originalBytes(file)));}
 public static void main(String[] args)throws Exception{
  Map<String,String> hashes=Map.of("GlassSurfaces.java","BE54D2CD2573D2AF88CB13CE5FEFCF8B33675D3C92C569A988A61EA5306C6914","NativeCoverGlass.java","9C4E040B615FA69354CCC71158021C3380DCEC3F4D8299D362DBCC92555252CF","RankLayoutFix.java","3A8173BCE383C1602ED8A8109E52047E8428AD40991F5A1BBFC44BBBFDBC501E");
  for(var e:hashes.entrySet())if(!originalHash(e.getKey()).equals(e.getValue()))throw new AssertionError("baseline render bytes changed: "+e.getKey());
  for(String row:Files.readAllLines(Path.of("tests/fixtures/HostSymbols-569-hashes.tsv"))){String[] p=row.split("\t");if(!originalHash(p[0]).equalsIgnoreCase(p[1]))throw new AssertionError("baseline changed: "+p[0]);}
  if(!HostSymbols.isBaseline())throw new AssertionError("uninitialized test resolver must preserve baseline identity");
  for(String n:List.of("dsv","gat","hlf","gi1","fue","hp"))if(!n.equals(HostSymbols.canonicalResource(n))||!n.equals(HostSymbols.resourceName(n)))throw new AssertionError(n);
  System.out.println("HostBaselineEquivalenceTest passed (strict inverse substitutions and original renderer SHA identities)");
 }
}
