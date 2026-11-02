package com.filehub.builder;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final int PICK_ZIP=41;
    private EditText owner, repo, token;
    private TextView picked, status;
    private Uri zipUri;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(24,24,24,24);
        ScrollView scroll=new ScrollView(this); scroll.addView(root);
        TextView title=new TextView(this); title.setText("FileHub APK Builder"); title.setTextSize(26); title.setPadding(0,0,0,12); root.addView(title);
        TextView info=new TextView(this); info.setText("Выбери Android-проект ZIP. Приложение загрузит его в GitHub и запустит сборку APK."); info.setTextSize(16); root.addView(info);
        owner=field("GitHub владелец (например, arturtymoshenko16-stack)"); root.addView(owner);
        repo=field("Репозиторий (например, go-go-xcode)"); root.addView(repo);
        token=field("GitHub Personal Access Token"); token.setInputType(129); root.addView(token);
        TextView warning=new TextView(this); warning.setText("Нужны права Contents: Read and write и Actions: Read and write. Не отправляй токен никому. Приложение не сохраняет его."); warning.setTextSize(13); warning.setPadding(0,8,0,12); root.addView(warning);
        Button choose=new Button(this); choose.setText("1. Выбрать ZIP проекта"); root.addView(choose);
        picked=new TextView(this); picked.setText("ZIP не выбран"); root.addView(picked);
        Button build=new Button(this); build.setText("2. Загрузить ZIP и запустить сборку APK"); root.addView(build);
        Button actions=new Button(this); actions.setText("Открыть GitHub Actions"); root.addView(actions);
        status=new TextView(this); status.setText("Готов к работе"); status.setTextSize(15); status.setPadding(0,16,0,0); root.addView(status);
        setContentView(scroll);
        choose.setOnClickListener(v->pickZip());
        build.setOnClickListener(v->startBuild());
        actions.setOnClickListener(v->{String o=owner.getText().toString().trim(),r=repo.getText().toString().trim(); if(o.isEmpty()||r.isEmpty()){msg("Укажи владельца и репозиторий");return;} open("https://github.com/"+o+"/"+r+"/actions");});
    }
    private EditText field(String h){EditText e=new EditText(this);e.setHint(h);e.setSingleLine(true);return e;}
    private void pickZip(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");startActivityForResult(i,PICK_ZIP);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==PICK_ZIP&&res==RESULT_OK&&data!=null){zipUri=data.getData();picked.setText("Выбран ZIP: "+zipUri.getLastPathSegment());}}
    private void startBuild(){
        final String o=owner.getText().toString().trim(),r=repo.getText().toString().trim(),t=token.getText().toString().trim();
        if(o.isEmpty()||r.isEmpty()||t.isEmpty()||zipUri==null){msg("Заполни поля и выбери ZIP");return;}
        status.setText("Загрузка ZIP в GitHub...");
        new Thread(()->{try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();InputStream in=getContentResolver().openInputStream(zipUri);if(in==null)throw new Exception("Не удалось открыть ZIP");
            byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){bytes.write(buf,0,n);if(bytes.size()>25*1024*1024)throw new Exception("ZIP больше 25 МБ");}in.close();
            String api="https://api.github.com/repos/"+o+"/"+r+"/contents/build-input/base.zip";
            JSONObject old=request(api,"GET",t,null);String sha=old.optString("sha","");
            JSONObject body=new JSONObject();body.put("message","Upload project ZIP from FileHub APK Builder");body.put("content",Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP));body.put("branch","main");if(!sha.isEmpty())body.put("sha",sha);
            request(api,"PUT",t,body);
            JSONObject dispatch=new JSONObject();dispatch.put("ref","main");
            request("https://api.github.com/repos/"+o+"/"+r+"/actions/workflows/build-combined-zips.yml/dispatches","POST",t,dispatch);
            runOnUiThread(()->{status.setText("ZIP загружен. Сборка запущена! Открой GitHub Actions и скачай Artifact Combined-Project-APK.");msg("Сборка запущена");});
        }catch(Exception e){String m=e.getMessage()==null?e.toString():e.getMessage();runOnUiThread(()->{status.setText("Ошибка: "+m);msg("Не удалось запустить сборку");});}}).start();
    }
    private JSONObject request(String address,String method,String t,JSONObject body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setRequestMethod(method);c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");c.setRequestProperty("Authorization","Bearer "+t);
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");OutputStream out=c.getOutputStream();out.write(body.toString().getBytes("UTF-8"));out.close();}
        int code=c.getResponseCode();InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();ByteArrayOutputStream b=new ByteArrayOutputStream();if(stream!=null){byte[] buf=new byte[4096];int n;while((n=stream.read(buf))!=-1)b.write(buf,0,n);stream.close();}String s=b.toString("UTF-8");c.disconnect();if(code==404 && "GET".equals(method)) return new JSONObject();if(code<200||code>=300)throw new Exception("GitHub API "+code+": "+s);return s.isEmpty()?new JSONObject():new JSONObject(s);
    }
    private void open(String u){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}
    private void msg(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
