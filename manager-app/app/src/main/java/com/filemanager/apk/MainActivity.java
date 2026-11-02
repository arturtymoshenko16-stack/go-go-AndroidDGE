package com.filemanager.apk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int PICK_FOLDER = 10;
    private DocumentFile rootDir, currentDir;
    private final List<DocumentFile> entries = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView pathView;
    private ListView listView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(18, 18, 18, 12);
        page.setBackgroundColor(0xFF101018);
        TextView heading = new TextView(this);
        heading.setText("File Manager APK"); heading.setTextSize(25); heading.setTextColor(0xFFFFFFFF);
        page.addView(heading);
        pathView = new TextView(this); pathView.setTextColor(0xFFBDB4D8); pathView.setPadding(0,8,0,12); page.addView(pathView);
        LinearLayout actions = new LinearLayout(this);
        addButton(actions, "Выбрать папку", v -> chooseFolder());
        addButton(actions, "Новый TXT", v -> createText());
        addButton(actions, "Обновить", v -> refresh());
        page.addView(actions);
        listView = new ListView(this); adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter); page.addView(listView, new LinearLayout.LayoutParams(-1,0,1));
        listView.setOnItemClickListener((p,v,pos,id) -> openEntry(entries.get(pos)));
        listView.setOnItemLongClickListener((p,v,pos,id) -> { showActions(entries.get(pos)); return true; });
        Button up = new Button(this); up.setText("← Назад в папку"); up.setOnClickListener(v -> goUp()); page.addView(up);
        setContentView(page);
        getWindow().setStatusBarColor(0xFF101018); getWindow().setNavigationBarColor(0xFF101018);
        refresh();
    }

    private void addButton(LinearLayout row, String label, View.OnClickListener click) {
        Button b = new Button(this); b.setText(label); b.setTextSize(11); b.setOnClickListener(click);
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }
    private void chooseFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_FOLDER);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_FOLDER && result == RESULT_OK && data != null) {
            Uri uri = data.getData(); int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags);
            rootDir = DocumentFile.fromTreeUri(this, uri); currentDir = rootDir; refresh();
        }
    }
    private void refresh() {
        entries.clear(); adapter.clear();
        if (currentDir == null || !currentDir.canRead()) { pathView.setText("Сначала выбери папку для работы с файлами"); adapter.add("Нажми «Выбрать папку»"); return; }
        pathView.setText("Папка: " + currentDir.getName());
        DocumentFile[] children = currentDir.listFiles();
        java.util.Arrays.sort(children, (a,b) -> { if (a.isDirectory()!=b.isDirectory()) return a.isDirectory()?-1:1; return String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName())); });
        for (DocumentFile f : children) { entries.add(f); adapter.add((f.isDirectory()?"📁  ":iconFor(f.getName())+"  ") + f.getName()); }
        if (entries.isEmpty()) adapter.add("Папка пуста");
        adapter.notifyDataSetChanged();
    }
    private String iconFor(String name) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.endsWith(".apk") || n.endsWith(".xapk") || n.endsWith(".apkm") || n.endsWith(".apks")) return "📦";
        if (n.endsWith(".txt") || n.endsWith(".lua") || n.endsWith(".json") || n.endsWith(".xml")) return "📄";
        if (n.endsWith(".zip")) return "🗜️";
        return "📃";
    }
    private void openEntry(DocumentFile f) {
        if (f.isDirectory()) { currentDir = f; refresh(); return; }
        String name = f.getName() == null ? "" : f.getName().toLowerCase();
        if (name.endsWith(".txt") || name.endsWith(".lua") || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".md")) { editText(f); return; }
        try { Intent i = new Intent(Intent.ACTION_VIEW); i.setDataAndType(f.getUri(), mime(name)); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(i); }
        catch (Exception e) { toast("Не найдено приложение для открытия этого файла"); }
    }
    private String mime(String n) {
        if (n.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (n.endsWith(".zip")) return "application/zip";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".pdf")) return "application/pdf";
        return "*/*";
    }
    private void showActions(DocumentFile f) {
        String[] options = f.isDirectory() ? new String[]{"Открыть", "Переименовать", "Удалить"} : new String[]{"Открыть / редактировать", "Переименовать", "Копировать", "Удалить"};
        new AlertDialog.Builder(this).setTitle(f.getName()).setItems(options, (d,w) -> {
            if (w==0) openEntry(f); else if (w==1) rename(f); else if (options[w].equals("Копировать")) copy(f); else if (options[w].equals("Удалить")) confirmDelete(f);
        }).show();
    }
    private void rename(DocumentFile f) {
        EditText input = new EditText(this); input.setSingleLine(true); input.setText(f.getName());
        new AlertDialog.Builder(this).setTitle("Новое имя").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{ String n=input.getText().toString().trim(); if(!n.isEmpty() && f.renameTo(n)) toast("Переименовано"); else toast("Не удалось переименовать"); refresh(); }).show();
    }
    private void confirmDelete(DocumentFile f) { new AlertDialog.Builder(this).setTitle("Удалить файл?").setMessage(f.getName()).setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{ if(f.delete()) toast("Удалено"); else toast("Не удалось удалить"); refresh(); }).show(); }
    private void copy(DocumentFile source) {
        if (source.isDirectory()) { toast("Копирование папок пока не поддерживается"); return; }
        try { DocumentFile target=currentDir.createFile(source.getType()==null?"application/octet-stream":source.getType(), "Копия_"+source.getName());
            if(target==null) throw new Exception("create failed");
            try(InputStream in=getContentResolver().openInputStream(source.getUri()); OutputStream out=getContentResolver().openOutputStream(target.getUri())) { byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0) out.write(buf,0,n); }
            toast("Копия создана"); refresh();
        } catch(Exception e) { toast("Ошибка копирования"); }
    }
    private void createText() {
        EditText input=new EditText(this); input.setSingleLine(true); input.setText("D.txt");
        new AlertDialog.Builder(this).setTitle("Создать текстовый файл").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Создать",(d,w)->{
            if(currentDir==null){toast("Сначала выбери папку");return;} String name=input.getText().toString().trim(); if(name.isEmpty()) return; if(!name.contains(".")) name += ".txt";
            DocumentFile f=currentDir.createFile("text/plain",name); if(f==null) toast("Не удалось создать файл"); else { toast("Файл создан"); editText(f); refresh(); }
        }).show();
    }
    private void editText(DocumentFile f) {
        try { StringBuilder content=new StringBuilder(); try(BufferedReader r=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(f.getUri())))){ String line; while((line=r.readLine())!=null) content.append(line).append('\n'); }
            EditText editor=new EditText(this); editor.setGravity(Gravity.TOP|Gravity.LEFT); editor.setMinLines(12); editor.setText(content.toString());
            new AlertDialog.Builder(this).setTitle(f.getName()).setView(editor).setNegativeButton("Закрыть",null).setPositiveButton("Сохранить",(d,w)->{
                try(OutputStream out=getContentResolver().openOutputStream(f.getUri(),"wt"); OutputStreamWriter writer=new OutputStreamWriter(out)){ writer.write(editor.getText().toString()); writer.flush(); toast("Сохранено"); }
                catch(Exception e){toast("Ошибка сохранения");}
            }).show();
        } catch(Exception e) { toast("Не удалось прочитать файл"); }
    }
    private void goUp() { if(currentDir==null || rootDir==null || currentDir.getUri().equals(rootDir.getUri())) return; DocumentFile p=currentDir.getParentFile(); if(p!=null) currentDir=p; refresh(); }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
}
