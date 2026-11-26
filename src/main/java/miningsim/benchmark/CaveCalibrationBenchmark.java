package miningsim.benchmark;

import miningsim.generation.WorldGenerationConfig;
import miningsim.generation.WorldGenerator;
import miningsim.world.Material;

/** Fixed seed headless calibration, 32 consecutive seeds and two explicit cases. */
public final class CaveCalibrationBenchmark {
    public static void main(String[] args) {
        for(int size:new int[]{8,4}) {
        long acceptedSum=0,geometrySum=0,rejectedSum=0,areaSum=0,componentSum=0; int zero=0,minimum=Integer.MAX_VALUE,maximum=0,minWidth=Integer.MAX_VALUE,maxWidth=0,minHeight=Integer.MAX_VALUE,maxHeight=0,top=Integer.MAX_VALUE,bottom=0; double percentSum=0;
        for(int seedIndex=0;seedIndex<34;seedIndex++) {
            int seed=seedIndex<32?seedIndex:seedIndex==32?357355:12345;
            var config=WorldGenerationConfig.preset(seed,size); var g=WorldGenerator.generate(config); var c=g.caves();
            int cols=config.width()/size,rows=(config.height()-1)/size; boolean[] seen=new boolean[cols*rows];
            int components=0,min=Integer.MAX_VALUE,max=0,total=0,minW=Integer.MAX_VALUE,maxW=0,minH=Integer.MAX_VALUE,maxH=0,minY=Integer.MAX_VALUE,maxY=0;
            for(int y=0;y<rows;y++) for(int x=0;x<cols;x++) {
                int index=y*cols+x; if(seen[index] || y*size<WorldGenerator.layers(config,x*size).stoneTop() || g.session().world().get(x*size,y*size)!=Material.AIR) continue;
                components++; var queue=new java.util.ArrayDeque<Integer>(); queue.add(index); seen[index]=true; int area=0,l=x,r=x,t=y,b=y;
                while(!queue.isEmpty()) { int p=queue.removeFirst(),px=p%cols,py=p/cols; area++; l=Math.min(l,px); r=Math.max(r,px); t=Math.min(t,py); b=Math.max(b,py);
                    for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) { int nx=px+d[0],ny=py+d[1]; if(nx<0 || nx>=cols || ny<0 || ny>=rows) continue; int ni=ny*cols+nx;
                        if(!seen[ni] && ny*size>=WorldGenerator.layers(config,nx*size).stoneTop() && g.session().world().get(nx*size,ny*size)==Material.AIR) { seen[ni]=true; queue.add(ni); } }
                }
                min=Math.min(min,area*size*size); max=Math.max(max,area*size*size); total+=area*size*size;
                minW=Math.min(minW,(r-l+1)*size); maxW=Math.max(maxW,(r-l+1)*size); minH=Math.min(minH,(b-t+1)*size); maxH=Math.max(maxH,(b-t+1)*size); minY=Math.min(minY,t*size); maxY=Math.max(maxY,b*size);
            }
            System.out.printf(java.util.Locale.ROOT,"size=%d seed=%d candidates=%d accepted=%d geometry=%d support=%d removed=%d components=%d area=%d minMeanMax=%d/%.1f/%d width=%d..%d height=%d..%d depth=%d..%d candidateMs=%.3f supportMs=%.3f%n",size,seed,c.candidates(),c.accepted(),c.geometryRejected(),c.supportRejected(),c.blocksRemoved(),components,total,components==0?0:min,components==0?0:(double)total/components,max,components==0?0:minW,maxW,components==0?0:minH,maxH,components==0?0:minY,maxY,c.candidateNanos()/1e6,c.supportNanos()/1e6);
            if(seedIndex<32) {
                acceptedSum+=c.accepted(); geometrySum+=c.geometryRejected(); rejectedSum+=c.supportRejected(); areaSum+=total; componentSum+=components;
                percentSum+=100.0*c.blocksRemoved()/(g.session().blocks().size()+c.blocksRemoved()); if(components==0) zero++;
                if(components>0) { minimum=Math.min(minimum,min); maximum=Math.max(maximum,max); minWidth=Math.min(minWidth,minW); maxWidth=Math.max(maxWidth,maxW); minHeight=Math.min(minHeight,minH); maxHeight=Math.max(maxHeight,maxH); top=Math.min(top,minY); bottom=Math.max(bottom,maxY); }
            }
        }
        System.out.printf(java.util.Locale.ROOT,"SUMMARY size=%d seeds=32 candidates=640 accepted=%d geometry=%d support=%d components=%d zero=%d meanRemovedPercent=%.3f totalArea=%d areaMinMeanMax=%d/%.1f/%d width=%d..%d height=%d..%d depth=%d..%d%n",size,acceptedSum,geometrySum,rejectedSum,componentSum,zero,percentSum/32,areaSum,minimum,(double)areaSum/componentSum,maximum,minWidth,maxWidth,minHeight,maxHeight,top,bottom);
        }
    }
}
