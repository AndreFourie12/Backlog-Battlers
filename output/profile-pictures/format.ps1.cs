using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
public static class AvatarFormat {
 public static void Run(string src,string dst,int id) {
  using(var im=new Bitmap(src)) {
   int l=im.Width,t=im.Height,r=-1,b=-1;
   for(int y=0;y<im.Height;y++) for(int x=0;x<im.Width;x++) {
    var p=im.GetPixel(x,y);
    bool visible=p.A>20;
    if(id==4 && p.R>238 && p.G>238 && p.B>238) visible=false;
    if(visible) { l=Math.Min(l,x);r=Math.Max(r,x);t=Math.Min(t,y);b=Math.Max(b,y); }
   }
   if(r<l) throw new Exception("Empty image");
   float side=Math.Min(r-l+1,b-t+1);
   float cx=(l+r+1)/2f,cy=(t+b+1)/2f;
   if(id>=25) side*=0.88f;
   if(id==6) cy=t+side/2f;
   using(var hi=new Bitmap(1024,1024,PixelFormat.Format32bppArgb)) {
    using(var g=Graphics.FromImage(hi)) {
     g.Clear(Color.Transparent);g.InterpolationMode=InterpolationMode.HighQualityBicubic;
     g.PixelOffsetMode=PixelOffsetMode.HighQuality;
     g.DrawImage(im,new RectangleF(0,0,1024,1024),new RectangleF(cx-side/2,cy-side/2,side,side),GraphicsUnit.Pixel);
    }
    for(int y=0;y<1024;y++) for(int x=0;x<1024;x++) {
     double dx=x+0.5-512,dy=y+0.5-512;
     double coverage=Math.Max(0,Math.Min(1,512-Math.Sqrt(dx*dx+dy*dy)));
     var p=hi.GetPixel(x,y);
     if(coverage<1) hi.SetPixel(x,y,Color.FromArgb((int)(p.A*coverage),p.R,p.G,p.B));
    }
    using(var result=new Bitmap(512,512,PixelFormat.Format32bppArgb)) {
     using(var g=Graphics.FromImage(result)) {g.InterpolationMode=InterpolationMode.HighQualityBicubic;g.CompositingQuality=CompositingQuality.HighQuality;g.DrawImage(hi,0,0,512,512);}
     result.Save(dst,ImageFormat.Png);
    }
   }
  }
 }
}
