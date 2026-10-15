package org.peyilo.ireadview.internal.effect

// Same inverse UV, lighting and receiver selection as the Web demo; GLES2/Android UV adapter.
internal val boundPageCurlFragment = """

precision highp float;
varying vec2 screenUv;
#define color gl_FragColor
uniform sampler2D currentPage;
uniform sampler2D nextPage;
uniform float progress;
uniform float aspect;
uniform float radiusFraction;
uniform float tilt;
uniform float shadowStrength;
uniform float lightSize;
uniform float lighting;
uniform float rightToLeft;
uniform vec3 backTint;
uniform float backContentAlpha;
vec2 pageUv(){return vec2(rightToLeft>.5?1.-screenUv.x:screenUv.x,screenUv.y);}
#define uv pageUv()
vec4 samplePage(sampler2D page,vec2 q){
  vec2 coord=vec2(rightToLeft>.5?1.-q.x:q.x,1.-q.y);
  vec4 pixel=texture2D(page,coord);
  if(pixel.a>0.)pixel.rgb/=pixel.a;
  return pixel;
}
const float PI=3.141592653589793;
bool onPaper(vec2 q){return q.x>=0. && q.x<=aspect && q.y>=0. && q.y<=1.;}
vec4 front(vec2 q){return samplePage(currentPage,q/vec2(aspect,1.));}
// Receiver-to-light ray intersects the same cylindrical material surface as the UV map.
float blocksLight(vec3 receiver,vec2 slope,vec2 n,float a,float r,float rootX,float rootZ){
  float d=dot(receiver.xy,n)-a+rootX, h=dot(slope,n);
  float zc=r-rootZ-receiver.z;
  float A=1.+h*h, B=2.*(d*h-zc), C=d*d+zc*zc-r*r;
  float discriminant=B*B-4.*A*C;
  float closest=-B/(2.*A);
  float distanceToAxis=length(vec2(d+h*closest,closest-zc));
  float feather=max(.0012,max(closest,0.)*lightSize*.20);
  float coverage=0.;
  // Continuous footprint coverage instead of a binary vote per light sample.
  // Neighboring samples overlap, removing the contour bands of discrete ray counts.
  if(closest>0. && distanceToAxis<r+feather){
    float delta=sqrt(max(discriminant,0.));
    float silhouette=1.-smoothstep(-feather,feather,distanceToAxis-r);
    for(int i=0;i<2;i++){
      float z=(-B+(i==0?-delta:delta))/(2.*A);
      float x=d+h*z;
      if(z>.0002 && x>=-feather){
        float angle=atan(max(x,0.),r-z-rootZ-receiver.z);
        vec2 hit=receiver.xy+slope*z+n*rootX;
        vec2 material=hit-n*x+n*r*angle;
        float edge=min(min(material.x,aspect-material.x),min(material.y,1.-material.y));
        float finiteSheet=smoothstep(-feather,feather,edge);
        coverage=max(coverage,silhouette*finiteSheet*smoothstep(-feather,feather,x));
      }
    }
  }
  float z=2.*r-rootZ-receiver.z;
  if(z>.0002){
    vec2 hit=receiver.xy+slope*z+n*rootX;
    float x=dot(hit,n)-a;
    vec2 material=hit-n*x+n*(PI*r-x);
    float edge=min(min(material.x,aspect-material.x),min(material.y,1.-material.y));
    float width=max(.0012,z*lightSize*.20);
    coverage=max(coverage,(1.-smoothstep(-width,width,x))*smoothstep(-width,width,edge));
  }
  return coverage;
}
float castShadow(vec3 receiver,vec2 n,float a,float r,float rootX,float rootZ){
  float occlusion=0.;
  // Deterministic area-light quadrature: no temporal noise, softness grows with height.
  for(int i=0;i<48;i++){
    float angle=float(i)*2.39996323;
    float distance=lightSize*sqrt((float(i)+.5)/48.);
    vec2 slope=vec2(-.60,.72)+distance*vec2(cos(angle),sin(angle));
    occlusion+=blocksLight(receiver,slope,n,a,r,rootX,rootZ);
  }
  return occlusion/48.;
}
vec4 receiveShadow(vec4 surface,vec3 receiver,vec2 n,float a,float r,float rootX,float rootZ){
  if(lighting<.5)return surface;
  float shadow=shadowStrength*castShadow(receiver,n,a,r,rootX,rootZ);
  surface.rgb*=mix(vec3(1.),vec3(.78,.81,.87),shadow);
  surface.rgb*=1.-shadow*.80;
  // Sub-LSB, stationary spatial dither avoids display quantization bands without flicker.
  float noise=fract(52.9829189*fract(dot(gl_FragCoord.xy,vec2(.06711056,.00583715))))-.5;
  surface.rgb+=noise/255.;
  return surface;
}
float paperLight(vec2 n,float normalZ,bool back){
  if(lighting<.5)return .82+.18*abs(normalZ);
  float angle=acos(clamp(normalZ,-1.,1.));
  vec3 normal=vec3(-n*sin(angle),normalZ);
  if(back)normal=-normal;
  vec3 light=normalize(vec3(-.60,.72,1.));
  float diffuse=max(0.,dot(normal,light));
  // Normalize against the flat sheet, retaining ambient illumination in the roll.
  return clamp((.65+.35*diffuse)/(.65+.35*light.z),.68,1.05);
}
vec4 verso(vec2 q,vec2 n,float normalZ){
  vec4 ink=front(q);
  ink.rgb=mix(backTint,ink.rgb,backContentAlpha);
  ink.rgb*=paperLight(n,normalZ,true);
  return ink;
}
void shadePage(){
  if(progress<=0.){color=samplePage(currentPage,uv);return;}
  if(progress>=1.){color=samplePage(nextPage,uv);return;}
  float p=progress;
  float r=aspect*radiusFraction*(.75+.25*sin(PI*p));
  // Fade diagonal tilt before the rolling band can reach the binding.
  float t=tilt*sin(PI*p)*(1.-smoothstep(.35,.62,p));
  vec2 n=normalize(vec2(1.,t));
  float spineMax=max(0.,n.y);
  float outerMax=aspect*n.x+spineMax;
  float a=(1.-p)*outerMax-PI*r*p;
  if(abs(t)>0.)a=max(a,spineMax);
  // With horizontal cross-sections, subtract the root primitive to pin the spine.
  float rootAngle=clamp(-a/r,0.,PI);
  float rootShift=a<0. ? a+r*sin(rootAngle) : 0.;
  float rootZ=a<0. ? r*(1.-cos(rootAngle)) : 0.;
  vec2 screen=uv*vec2(aspect,1.);
  vec2 original=screen+n*rootShift;
  float d=dot(original,n)-a;
  vec2 foot=original-n*d;
  vec4 underneath=samplePage(nextPage,uv);
  float visible=rootAngle<PI*.5 ? clamp((a+r-rootShift)/r,0.,1.) : 0.;
  float shadow=.15*visible*sin(PI*p)*exp(-abs(d-r)/max(.25*r,.00001));
  if(lighting<.5)underneath.rgb*=1.-shadow;
  if(d<0.){
    vec2 backQ=foot+n*(PI*r-d);
    if(onPaper(backQ)){color=receiveShadow(verso(backQ,n,-1.),vec3(screen,2.*r-rootZ),n,a,r,rootShift,rootZ);return;}
    if(onPaper(original) && dot(original,n)<=a){color=receiveShadow(front(original),vec3(screen,-rootZ),n,a,r,rootShift,rootZ);return;}
    color=receiveShadow(underneath,vec3(screen,0.),n,a,r,rootShift,rootZ);return;
  }
  if(d>r){color=receiveShadow(underneath,vec3(screen,0.),n,a,r,rootShift,rootZ);return;}
  float theta=asin(clamp(d/r,0.,1.));
  vec2 backQ=foot+n*r*(PI-theta);
  vec2 frontQ=foot+n*r*theta;
  if(onPaper(backQ)){color=receiveShadow(verso(backQ,n,-cos(theta)),vec3(screen,r*(1.+cos(theta))-rootZ),n,a,r,rootShift,rootZ);return;}
  if(onPaper(frontQ)){
    color=front(frontQ);color.rgb*=paperLight(n,cos(theta),false);
    color=receiveShadow(color,vec3(screen,r*(1.-cos(theta))-rootZ),n,a,r,rootShift,rootZ);return;
  }
  color=receiveShadow(underneath,vec3(screen,0.),n,a,r,rootShift,rootZ);
}

void main(){shadePage();color.rgb*=color.a;}
"""
