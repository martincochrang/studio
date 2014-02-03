tinyMCEPopup.requireLangPack();
var ed;
function init(){ed=tinyMCEPopup.editor;
tinyMCEPopup.resizeToInnerSize();
document.getElementById("backgroundimagebrowsercontainer").innerHTML=getBrowserHTML("backgroundimagebrowser","backgroundimage","image","table");
document.getElementById("bordercolor_pickcontainer").innerHTML=getColorPickerHTML("bordercolor_pick","bordercolor");
document.getElementById("bgcolor_pickcontainer").innerHTML=getColorPickerHTML("bgcolor_pick","bgcolor");
var g=ed;
var i=ed.dom.getParent(ed.selection.getStart(),"td,th");
var h=document.forms[0];
var o=ed.dom.parseStyle(ed.dom.getAttrib(i,"style"));
var e=i.nodeName.toLowerCase();
var j=ed.dom.getAttrib(i,"align");
var l=ed.dom.getAttrib(i,"valign");
var b=trimSize(getStyle(i,"width","width"));
var m=trimSize(getStyle(i,"height","height"));
var f=convertRGBToHex(getStyle(i,"bordercolor","borderLeftColor"));
var q=convertRGBToHex(getStyle(i,"bgcolor","backgroundColor"));
var k=ed.dom.getAttrib(i,"class");
var p=getStyle(i,"background","backgroundImage").replace(new RegExp("url\\(['\"]?([^'\"]*)['\"]?\\)","gi"),"$1");
var a=ed.dom.getAttrib(i,"id");
var c=ed.dom.getAttrib(i,"lang");
var d=ed.dom.getAttrib(i,"dir");
var n=ed.dom.getAttrib(i,"scope");
addClassesToList("class","table_cell_styles");
TinyMCE_EditableSelects.init();
if(!ed.dom.hasClass(i,"mceSelected")){h.bordercolor.value=f;
h.bgcolor.value=q;
h.backgroundimage.value=p;
h.width.value=b;
h.height.value=m;
h.id.value=a;
h.lang.value=c;
h.style.value=ed.dom.serializeStyle(o);
selectByValue(h,"align",j);
selectByValue(h,"valign",l);
selectByValue(h,"class",k,true,true);
selectByValue(h,"celltype",e);
selectByValue(h,"dir",d);
selectByValue(h,"scope",n);
if(isVisible("backgroundimagebrowser")){document.getElementById("backgroundimage").style.width="180px"
}updateColor("bordercolor_pick","bordercolor");
updateColor("bgcolor_pick","bgcolor")
}else{tinyMCEPopup.dom.hide("action")
}}function updateAction(){var a,e=ed,g,k,h,f=document.forms[0];
if(!AutoValidator.validate(f)){tinyMCEPopup.alert(AutoValidator.getErrorMessages(f).join(". ")+".");
return false
}tinyMCEPopup.restoreSelection();
a=ed.selection.getStart();
g=ed.dom.getParent(a,"td,th");
k=ed.dom.getParent(a,"tr");
h=ed.dom.getParent(a,"table");
if(ed.dom.hasClass(g,"mceSelected")){tinymce.each(ed.dom.select("td.mceSelected,th.mceSelected"),function(i){updateCell(i)
});
ed.addVisual();
ed.nodeChanged();
e.execCommand("mceEndUndoLevel");
tinyMCEPopup.close();
return
}switch(getSelectValue(f,"action")){case"cell":var d=getSelectValue(f,"celltype");
var m=getSelectValue(f,"scope");
function l(i){if(i){updateCell(g);
ed.addVisual();
ed.nodeChanged();
e.execCommand("mceEndUndoLevel");
tinyMCEPopup.close()
}}if(ed.getParam("accessibility_warnings",1)){if(d=="th"&&m==""){tinyMCEPopup.confirm(ed.getLang("table_dlg.missing_scope","",true),l)
}else{l(1)
}return
}updateCell(g);
break;
case"row":var j=k.firstChild;
if(j.nodeName!="TD"&&j.nodeName!="TH"){j=nextCell(j)
}do{j=updateCell(j,true)
}while((j=nextCell(j))!=null);
break;
case"col":var o,b=0,j=k.firstChild,n=h.getElementsByTagName("tr");
if(j.nodeName!="TD"&&j.nodeName!="TH"){j=nextCell(j)
}do{if(j==g){break
}b+=j.getAttribute("colspan")
}while((j=nextCell(j))!=null);
for(var c=0;
c<n.length;
c++){j=n[c].firstChild;
if(j.nodeName!="TD"&&j.nodeName!="TH"){j=nextCell(j)
}o=0;
do{if(o==b){j=updateCell(j,true);
break
}o+=j.getAttribute("colspan")
}while((j=nextCell(j))!=null)
}break;
case"all":var n=h.getElementsByTagName("tr");
for(var c=0;
c<n.length;
c++){var j=n[c].firstChild;
if(j.nodeName!="TD"&&j.nodeName!="TH"){j=nextCell(j)
}do{j=updateCell(j,true)
}while((j=nextCell(j))!=null)
}break
}ed.addVisual();
ed.nodeChanged();
e.execCommand("mceEndUndoLevel");
tinyMCEPopup.close()
}function nextCell(a){while((a=a.nextSibling)!=null){if(a.nodeName=="TD"||a.nodeName=="TH"){return a
}}return null
}function updateCell(d,j){var h=ed;
var i=document.forms[0];
var b=d.nodeName.toLowerCase();
var g=getSelectValue(i,"celltype");
var m=h.getDoc();
var e=ed.dom;
if(!j){e.setAttrib(d,"id",i.id.value)
}e.setAttrib(d,"align",i.align.value);
e.setAttrib(d,"vAlign",i.valign.value);
e.setAttrib(d,"lang",i.lang.value);
e.setAttrib(d,"dir",getSelectValue(i,"dir"));
e.setAttrib(d,"style",ed.dom.serializeStyle(ed.dom.parseStyle(i.style.value)));
e.setAttrib(d,"scope",i.scope.value);
e.setAttrib(d,"class",getSelectValue(i,"class"));
ed.dom.setAttrib(d,"width","");
ed.dom.setAttrib(d,"height","");
ed.dom.setAttrib(d,"bgColor","");
ed.dom.setAttrib(d,"borderColor","");
ed.dom.setAttrib(d,"background","");
d.style.width=getCSSSize(i.width.value);
d.style.height=getCSSSize(i.height.value);
if(i.bordercolor.value!=""){d.style.borderColor=i.bordercolor.value;
d.style.borderStyle=d.style.borderStyle==""?"solid":d.style.borderStyle;
d.style.borderWidth=d.style.borderWidth==""?"1px":d.style.borderWidth
}else{d.style.borderColor=""
}d.style.backgroundColor=i.bgcolor.value;
if(i.backgroundimage.value!=""){d.style.backgroundImage="url('"+i.backgroundimage.value+"')"
}else{d.style.backgroundImage=""
}if(b!=g){var f=m.createElement(g);
for(var k=0;
k<d.childNodes.length;
k++){f.appendChild(d.childNodes[k].cloneNode(1))
}for(var l=0;
l<d.attributes.length;
l++){ed.dom.setAttrib(f,d.attributes[l].name,ed.dom.getAttrib(d,d.attributes[l].name))
}d.parentNode.replaceChild(f,d);
d=f
}e.setAttrib(d,"style",e.serializeStyle(e.parseStyle(d.style.cssText)));
return d
}function changedBackgroundImage(){var a=document.forms[0];
var b=ed.dom.parseStyle(a.style.value);
b["background-image"]="url('"+a.backgroundimage.value+"')";
a.style.value=ed.dom.serializeStyle(b)
}function changedSize(){var b=document.forms[0];
var c=ed.dom.parseStyle(b.style.value);
var d=b.width.value;
if(d!=""){c.width=getCSSSize(d)
}else{c.width=""
}var a=b.height.value;
if(a!=""){c.height=getCSSSize(a)
}else{c.height=""
}b.style.value=ed.dom.serializeStyle(c)
}function changedColor(){var a=document.forms[0];
var b=ed.dom.parseStyle(a.style.value);
b["background-color"]=a.bgcolor.value;
b["border-color"]=a.bordercolor.value;
a.style.value=ed.dom.serializeStyle(b)
}function changedStyle(){var a=document.forms[0];
var b=ed.dom.parseStyle(a.style.value);
if(b["background-image"]){a.backgroundimage.value=b["background-image"].replace(new RegExp("url\\('?([^']*)'?\\)","gi"),"$1")
}else{a.backgroundimage.value=""
}if(b.width){a.width.value=trimSize(b.width)
}if(b.height){a.height.value=trimSize(b.height)
}if(b["background-color"]){a.bgcolor.value=b["background-color"];
updateColor("bgcolor_pick","bgcolor")
}if(b["border-color"]){a.bordercolor.value=b["border-color"];
updateColor("bordercolor_pick","bordercolor")
}}tinyMCEPopup.onInit.add(init);