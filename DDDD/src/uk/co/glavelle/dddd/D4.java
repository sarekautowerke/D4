package uk.co.glavelle.dddd;

import android.app.*;
import android.os.*;
import android.content.*;

import java.net.*;
import java.util.*;
import android.content.Intent;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import java.io.*;
import javax.xml.parsers.*;
import org.apache.http.client.*; import org.apache.http.impl.client.*; import org.apache.http.client.methods.*;


public class D4 extends Activity
{
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            // We can read and write the media
            Intent intent = getIntent();
            String url;
            
            try{
                url = (String) intent.getExtras().get(Intent.EXTRA_TEXT);
                url += "#view:list";
            }
            catch(Exception e){
                url = "https://www.dropbox.com/s/8owsfcia59ko76i#view:list"; //Used for testing if it's not opened from the dropox action menu
                //Obviously what needs to happen here is a message suggesting that the user use the dropbox app, and a textbox in case they've got the url copied.
            }
            debug(url);
            
            String page = getData(url);
            List filesAndFolders = parsePage(page);
            String dlDirPath = Environment.getExternalStorageDirectory() + "/download/" + getFolderName(page) + "/";
            getFilesFromFolder(filesAndFolders, dlDirPath);
            debug("Done!");
        }
        else {
            //Can't write files, so show a message and quit.
            
        }
    }
    
    public void getFilesFromFolder(List filesAndFolders, String savePath){
        // create a File object for the parent directory
        File downloadsDirectory = new File(savePath);
        //create the folder if needed.
        downloadsDirectory.mkdir();

        for (int i = 0; i < filesAndFolders.size(); i++){
           Object links = filesAndFolders.get(i);
           List linksArray = (ArrayList) links;
           if(i == 0){
               for (int j = 0; j < linksArray.size(); j+=2){
                   //We've got an array of file urls so download each one to a directory with the folder name
                   String fileURL = linksArray.get(j).toString();
                   String fileName = linksArray.get(j + 1).toString();
                   downloadFile(fileURL, savePath, fileName);
               }
           }
           else if(i == 1){
               //we've got an array of folders so recurse down the levels, extracting subfolders and files until we've downloaded everything.
               for (int j = 0; j < linksArray.size(); j+=2){
                   String folderURL = linksArray.get(j).toString();
                   String folderName = linksArray.get(j + 1).toString();
                   
                   String page = getData(folderURL);
                   List newFilesAndFolders = parsePage(page);
                   String dlDirPath = savePath + folderName + "/";

                   getFilesFromFolder(newFilesAndFolders, dlDirPath);
                    
               }
           }
        }
    }
    
    public void downloadFile(String url, String savePath, String fileName){
        
        try {
            URL theURL = new URL(url);
            InputStream input = theURL.openStream();
            OutputStream output = new FileOutputStream (new File(savePath, fileName));
            try {
                byte[] buffer = new byte[1024];
                int bytesRead = 0;
                while ((bytesRead = input.read(buffer, 0, buffer.length)) >= 0) {
                    output.write(buffer, 0, bytesRead);
                }
            } 
            catch(Exception e){
                debug(e.toString());
            }
            finally {
                output.close();
            }
        } 
        catch(Exception e){
            debug(e.toString());
        }
    }
    
    public String getData(String url){
        HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(url);
        String response = "";
        try{
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            response = client.execute(get, responseHandler);
        }
        catch(Exception e){
            debug(e.toString());
        }
        return response;
    }
    
    public String getFolderName(String pageCode){
        String usefulSection = pageCode.substring(pageCode.indexOf("<h3 id=\"breadcrumb\">"), pageCode.indexOf("<div id=\"list-view\" class=\"view\""));
        String folderName;
        try{
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource is = new InputSource();
            is.setCharacterStream(new StringReader(usefulSection));
            Document doc = db.parse(is);
            NodeList divs = doc.getElementsByTagName("h3");
            for (int i = 0; i < divs.getLength(); i++){
                Element div = (Element) divs.item(i);
                String a = div.getTextContent();
                folderName = a.substring(a.indexOf("/>") + 2).trim();
                return folderName;
            }  
        }
        catch(Exception e){
            debug(e.toString());  
        }
        return "Error!";
    }
    
    public List parsePage(String pageCode){
        List sections  = new ArrayList();
        List folders = new ArrayList();
        List files =  new ArrayList();
        int start = pageCode.indexOf("<div id=\"list-view\" class=\"view\"");
        int end =  pageCode.indexOf("<div id=\"gallery-view\" class=\"view\"");
        String usefulSection = "";
        if(start != -1 && end != -1){
             usefulSection = pageCode.substring(start, end);
        }
        else{
            debug("Could not parse page");
        }
        try{
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource is = new InputSource();
            is.setCharacterStream(new StringReader(usefulSection));
            Document doc = db.parse(is);
       
            NodeList divs = doc.getElementsByTagName("div");
            for (int i = 0; i < divs.getLength(); i++){
                Element div = (Element) divs.item(i);
                boolean isFolder = false;
                if (div.getAttribute("class").equals("filename")){
                    NodeList imgs = div.getElementsByTagName("img");
                    for (int j = 0; j < imgs.getLength(); j++){
                        Element img = (Element) imgs.item(j);
                        if(img.getAttribute("class").indexOf("folder") > 0){
                            isFolder = true;
                        }
                        else {
                            isFolder = false; //it's a file
                        }
                    }

                    NodeList anchors  = div.getElementsByTagName("a");
                    Element anchor = (Element) anchors.item(0);
                    String attr = anchor.getAttribute("href");
                    String fileName = anchor.getAttribute("title");
                    String fileURL;
                    if(isFolder && !attr.equals("#")){
                        folders.add(attr);
                        folders.add(fileName);
                    }
                    else if(!isFolder && !attr.equals("#")){
                        //Dropbox uses ajax to get the file for download, so the url isn't enough. We must be sneaky here.
                        fileURL = "https://dl.dropbox.com" + attr.substring(23) + "?dl=1";
                        files.add(fileURL);
                        files.add(fileName);
                    }
                }
            }
        }
        catch(Exception e){
            debug(e.toString());
        }

        sections.add(files);
        sections.add(folders);
        
        return sections;
    }
    
    public void debug(String message){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setNegativeButton("Ok", new DialogInterface.OnClickListener() {
           public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
           }
       });
        alertDialog.setMessage(message);
        
        AlertDialog balertDialog = alertDialog.create();
        balertDialog.show();
    }
}
