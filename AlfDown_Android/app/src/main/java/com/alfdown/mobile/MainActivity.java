package com.alfdown.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class MainActivity extends Activity {
    static final String BASE="https://alfred-dsm-shopify-exports-prod.s3.us-west-2.amazonaws.com/";
    static final String[] GROUP_NAMES={"현악기","목관악기","금관악기","타악기 외"};
    static final String[][] CODES={{"VN","VA","VC","SB"},{"P","F","O","C","AC","BC","B","AX","TX","BTX"},{"H","T","TN","BBC","BTC","TB"},{"M","TMP","PC","K","H"}};
    static final String[][] LABELS={{"바이올린","비올라","첼로","스트링베이스"},{"피콜로","플루트","오보에","클라리넷","알토클라리넷","베이스클라리넷","바순","알토색소폰","테너색소폰","바리톤색소폰"},{"호른","트럼펫","트롬본","유포늄 B.C.","유포늄 T.C.","튜바"},{"말렛퍼커션","팀파니","퍼커션","피아노","하프"}};
    static final Set<String> SINGLE=new HashSet<>(Arrays.asList("SB","BC","BTX","TMP","K"));
    static final String[] EXTS={"png","jpg","jpeg","webp"};
    final CheckBox[][] checks=new CheckBox[4][];
    final CheckBox[] extChecks=new CheckBox[4];
    EditText product,parts,pages,workers;
    CheckBox score;
    TextView folderLabel,status,log;
    ProgressBar progress;
    Button searchBtn,downloadBtn,pdfBtn;
    Uri folderUri;
    final AtomicBoolean stop=new AtomicBoolean(false);
    volatile boolean busy=false;
    final ArrayList<Hit> found=new ArrayList<>();
    final LinkedHashMap<String,Uri> downloaded=new LinkedHashMap<>();
    final ExecutorService runner=Executors.newSingleThreadExecutor();
    final int BG=Color.rgb(247,240,229), CARD=Color.rgb(255,250,242), INK=Color.rgb(81,66,54);
    static class Hit { final String name; final int group,order,part,page; Hit(String n,int g,int o,int p,int pg){name=n;group=g;order=o;part=p;page=pg;} }
    void ui(Runnable r){runOnUiThread(r);}
    TextView label(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextColor(INK);v.setTextSize(size);v.setPadding(5,8,5,8);return v;}
    EditText input(String text,boolean number){EditText v=new EditText(this);v.setSingleLine(true);v.setText(text);v.setTextColor(INK);if(number)v.setInputType(InputType.TYPE_CLASS_NUMBER);return v;}
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(14,8,14,8);return v;}
    Button button(String s,LinearLayout parent,View.OnClickListener click){Button b=new Button(this);b.setText(s);b.setOnClickListener(click);parent.addView(b);return b;}
    void notice(String s){ui(()->{status.setText(s);log.append(s+"\n");});}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);LinearLayout root=column();sc.addView(root);setContentView(sc);
        TextView hero=label("𝄞  AlfDown Mobile",27);root.addView(hero);root.addView(label("SIMSANGSO EDITION · Android 1.0",12));
        root.addView(label("상품 번호",15));product=input("00-PC-0019403",false);root.addView(product);
        root.addView(label("파트 최대 / 페이지 최대 / 동시 요청",14));LinearLayout settings=new LinearLayout(this);root.addView(settings);
        parts=input("3",true);pages=input("15",true);workers=input("6",true);
        for(EditText e:new EditText[]{parts,pages,workers}){settings.addView(e,new LinearLayout.LayoutParams(0,-2,1));}
        for(int g=0;g<4;g++){final int gg=g;LinearLayout card=column();card.setBackgroundColor(CARD);LinearLayout head=new LinearLayout(this);head.setGravity(android.view.Gravity.CENTER_VERTICAL);card.addView(head);TextView title=label(GROUP_NAMES[g],17);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button toggle=new Button(this);toggle.setText("그룹 선택/해제");head.addView(toggle);checks[g]=new CheckBox[CODES[g].length];
            for(int i=0;i<CODES[g].length;i++){CheckBox cb=new CheckBox(this);cb.setText(CODES[g][i]+"  "+LABELS[g][i]);cb.setTextColor(INK);cb.setChecked(g==0);checks[g][i]=cb;card.addView(cb);}
            toggle.setOnClickListener(v->{boolean all=true;for(CheckBox cb:checks[gg])all&=cb.isChecked();for(CheckBox cb:checks[gg])cb.setChecked(!all);});root.addView(card);Space space=new Space(this);root.addView(space,new LinearLayout.LayoutParams(1,10));}
        LinearLayout select=new LinearLayout(this);root.addView(select);button("전체 선택",select,v->setAll(true));button("전체 해제",select,v->setAll(false));
        root.addView(label("파일 확장자",16));LinearLayout exts=new LinearLayout(this);root.addView(exts);
        for(int i=0;i<4;i++){CheckBox cb=new CheckBox(this);cb.setText(EXTS[i]);cb.setTextColor(INK);cb.setChecked(i==0);extChecks[i]=cb;exts.addView(cb);}
        score=new CheckBox(this);score.setText("스코어 전용 검색 (상품번호-페이지.png)");score.setTextColor(INK);root.addView(score);
        folderLabel=label("저장 폴더: 미선택 (먼저 선택해주세요)",13);root.addView(folderLabel);
        button("저장 폴더 선택",root,v->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,1);});
        button("저장 폴더 열기",root,v->{if(folderUri==null){toast("저장 폴더를 먼저 선택하세요");return;}try{Intent intent=new Intent(Intent.ACTION_VIEW);intent.setDataAndType(folderUri,"vnd.android.document/directory");intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(intent);}catch(Exception e){toast("파일 앱에서 선택한 폴더를 열어주세요");}});
        searchBtn=button("악보 검색 시작",root,v->startSearch());button("검색 / 다운로드 중지",root,v->{stop.set(true);notice("중지 요청됨");});
        downloadBtn=button("발견 파일 다운로드",root,v->startDownload());pdfBtn=button("악기 순서대로 PDF 만들기",root,v->startPdf());
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);root.addView(progress);
        status=label("대기 중",14);root.addView(status);log=label("검색 결과가 여기에 표시됩니다.\n",12);root.addView(log);
        String stored=getPreferences(0).getString("tree",null);if(stored!=null){folderUri=Uri.parse(stored);folderLabel.setText("저장 폴더: "+folderUri.getLastPathSegment());}
    }
    void setAll(boolean yes){for(CheckBox[] group:checks)for(CheckBox cb:group)cb.setChecked(yes);}
    void toast(String s){ui(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==1&&result==RESULT_OK&&data!=null){folderUri=data.getData();try{getContentResolver().takePersistableUriPermission(folderUri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){}getPreferences(0).edit().putString("tree",folderUri.toString()).apply();folderLabel.setText("저장 폴더: "+folderUri.getLastPathSegment());}}
    int positive(EditText e,int max){int n=Integer.parseInt(e.getText().toString().trim());if(n<1||n>max)throw new IllegalArgumentException("설정 값은 1~"+max+" 범위여야 합니다");return n;}
    String productCode(){String s=product.getText().toString().trim();if(!s.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("상품 번호에 허용되지 않는 문자가 있습니다");return s;}
    List<Hit> candidates(){String p=productCode();int partMax=positive(parts,30),pageMax=positive(pages,150);ArrayList<String> extensions=new ArrayList<>();for(int i=0;i<4;i++)if(extChecks[i].isChecked())extensions.add(EXTS[i]);if(extensions.isEmpty())throw new IllegalArgumentException("확장자를 선택하세요");List<Hit> list=new ArrayList<>();if(score.isChecked()){for(int page=1;page<=pageMax;page++)for(String ext:extensions)list.add(new Hit(p+"-"+page+"."+ext,-1,0,0,page));}else{for(int g=0;g<4;g++)for(int i=0;i<CODES[g].length;i++)if(checks[g][i].isChecked()){String code=CODES[g][i];boolean single=SINGLE.contains(code)||(g==3&&code.equals("H"));for(int part=1;part<=(single?1:partMax);part++)for(int page=1;page<=pageMax;page++)for(String ext:extensions)list.add(new Hit(p+"_"+code+(single?"":part)+"-"+page+"."+ext,g,i,part,page));}}if(list.size()>200000)throw new IllegalArgumentException("검색 후보가 너무 많습니다");return list;}
    void lock(boolean value){busy=value;ui(()->{searchBtn.setEnabled(!value);downloadBtn.setEnabled(!value);pdfBtn.setEnabled(!value);});}
    void startSearch(){if(busy)return;final List<Hit> tasks;final int count;try{tasks=candidates();count=positive(workers,16);if(tasks.isEmpty())throw new IllegalArgumentException("악기를 선택하세요");}catch(Exception e){toast(e.getMessage());return;}stop.set(false);lock(true);synchronized(found){found.clear();downloaded.clear();}ui(()->{log.setText("");progress.setProgress(0);});runner.execute(()->{ExecutorService pool=Executors.newFixedThreadPool(count);AtomicInteger done=new AtomicInteger();ConcurrentLinkedQueue<Hit> hits=new ConcurrentLinkedQueue<>();try{List<Future<?>> futures=new ArrayList<>();for(Hit h:tasks){if(stop.get())break;futures.add(pool.submit(()->{if(stop.get())return;try{int status=head(h.name);if(status==200){hits.add(h);notice("발견: "+h.name);}else if(status==403)notice("접근 거부: "+h.name);}catch(Exception ignored){}int d=done.incrementAndGet();if(d%5==0||d==tasks.size())ui(()->{progress.setProgress((int)(100L*d/tasks.size()));status.setText("검색 "+d+" / "+tasks.size()+" · 발견 "+hits.size());});}));}for(Future<?> f:futures){if(stop.get()){for(Future<?> ff:futures)ff.cancel(true);break;}try{f.get();}catch(Exception ignored){}}synchronized(found){found.addAll(hits);found.sort(Comparator.comparingInt((Hit h)->h.group).thenComparingInt(h->h.order).thenComparingInt(h->h.part).thenComparingInt(h->h.page).thenComparing(h->h.name));}notice("검색 종료 · 발견 "+hits.size()+"개" );}finally{pool.shutdownNow();lock(false);}});}
    int head(String name)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(BASE+name).openConnection();try{c.setRequestMethod("HEAD");c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setInstanceFollowRedirects(false);return c.getResponseCode();}finally{c.disconnect();}}
    Uri findFile(String name){Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(folderUri,DocumentsContract.getTreeDocumentId(folderUri));try(android.database.Cursor cursor=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},DocumentsContract.Document.COLUMN_DISPLAY_NAME+"=?",new String[]{name},null)){if(cursor!=null)while(cursor.moveToNext()){if(name.equals(cursor.getString(1)))return DocumentsContract.buildDocumentUriUsingTree(folderUri,cursor.getString(0));}}catch(Exception ignored){}return null;}
    Uri createFile(String name,String mime)throws Exception{Uri old=findFile(name);if(old!=null)return old;Uri parent=DocumentsContract.buildDocumentUriUsingTree(folderUri,DocumentsContract.getTreeDocumentId(folderUri));Uri created=DocumentsContract.createDocument(getContentResolver(),parent,mime,name);if(created==null)throw new IOException("파일 생성 실패: "+name);return created;}
    void requireFolder(){if(folderUri==null)throw new IllegalArgumentException("저장 폴더를 먼저 선택하세요");}
    void startDownload(){if(busy)return;final ArrayList<Hit> list;try{requireFolder();synchronized(found){list=new ArrayList<>(found);}if(list.isEmpty())throw new IllegalArgumentException("먼저 악보를 검색하세요");}catch(Exception e){toast(e.getMessage());return;}stop.set(false);lock(true);runner.execute(()->{int done=0,ok=0;for(Hit h:list){if(stop.get())break;try{String mime=h.name.endsWith("png")?"image/png":h.name.endsWith("webp")?"image/webp":"image/jpeg";HttpURLConnection c=(HttpURLConnection)new URL(BASE+h.name).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(20000);if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buf=new byte[32768];int n;while((n=in.read(buf))!=-1){if(stop.get())throw new IOException("취소됨");bytes.write(buf,0,n);}}finally{c.disconnect();}if(stop.get())break;Uri uri=createFile(h.name,mime);try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("출력 열기 실패");bytes.writeTo(out);}synchronized(found){downloaded.put(h.name,uri);}ok++;notice("저장: "+h.name);}catch(Exception e){notice("실패: "+h.name+" · "+e.getMessage());}done++;final int d=done;ui(()->progress.setProgress((int)(100L*d/list.size())));}notice("다운로드 종료 · 성공 "+ok+" / "+list.size());lock(false);});}
    void startPdf(){if(busy)return;final ArrayList<Hit> list=new ArrayList<>();try{requireFolder();synchronized(found){for(Hit h:found)if(downloaded.containsKey(h.name))list.add(h);}if(list.isEmpty())throw new IllegalArgumentException("먼저 파일을 다운로드하세요");}catch(Exception e){toast(e.getMessage());return;}stop.set(false);lock(true);runner.execute(()->{PdfDocument pdf=new PdfDocument();int index=0;try{for(Hit h:list){if(stop.get())break;Uri uri=downloaded.get(h.name);Bitmap bitmap;try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in);}if(bitmap==null){notice("PDF 제외 (이미지 해석 실패): "+h.name);continue;}int w=bitmap.getWidth(),ht=bitmap.getHeight();float scale=Math.min(1f,2000f/Math.max(w,ht));int pw=Math.max(1,Math.round(w*scale)),ph=Math.max(1,Math.round(ht*scale));PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(pw,ph,++index).create();PdfDocument.Page page=pdf.startPage(info);Canvas canvas=page.getCanvas();canvas.drawColor(Color.WHITE);Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);canvas.drawBitmap(bitmap,null,new android.graphics.Rect(0,0,pw,ph),paint);pdf.finishPage(page);bitmap.recycle();final int d=index;ui(()->progress.setProgress((int)(100L*d/list.size())));}if(index>0){String name=productCode()+(score.isChecked()?"_Score":"_Parts")+".pdf";Uri out=createFile(name,"application/pdf");try(OutputStream stream=getContentResolver().openOutputStream(out,"wt")){pdf.writeTo(stream);}notice("PDF 저장 완료: "+name+" · "+index+"페이지");}else notice("PDF로 변환할 이미지가 없습니다");}catch(Exception e){notice("PDF 생성 실패: "+e.getMessage());}finally{pdf.close();lock(false);}});}
    @Override protected void onDestroy(){stop.set(true);runner.shutdownNow();super.onDestroy();}
}
