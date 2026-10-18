(() => {
  const tools = [
    {id:"text-case",name:"Text Case Converter",category:"Text",icon:"Aa",description:"Convert text to uppercase, lowercase, title case, or sentence case.",kind:"case"},
    {id:"word-counter",name:"Word & Character Counter",category:"Text",icon:"≡",description:"Count words, characters, sentences, and estimated reading time.",kind:"counter"},
    {id:"remove-duplicates",name:"Remove Duplicate Lines",category:"Text",icon:"☷",description:"Keep only the first occurrence of each line.",kind:"dedupe"},
    {id:"sort-lines",name:"Natural Line Sorter",category:"Text",icon:"↕",description:"Sort lines naturally so 2 comes before 10.",kind:"sort"},
    {id:"reverse-text",name:"Reverse Text",category:"Text",icon:"↶",description:"Reverse characters or reverse the order of lines.",kind:"reverse"},
    {id:"json-formatter",name:"JSON Formatter",category:"Developer",icon:"{}",description:"Validate and pretty-print JSON locally.",kind:"json"},
    {id:"base64",name:"Base64 Encoder / Decoder",category:"Developer",icon:"64",description:"Encode text to Base64 or decode Base64 back to text.",kind:"base64"},
    {id:"url-encode",name:"URL Encoder / Decoder",category:"Developer",icon:"↗",description:"Encode or decode URL components.",kind:"url"},
    {id:"html-escape",name:"HTML Escape",category:"Developer",icon:"</>",description:"Escape or unescape HTML-sensitive characters.",kind:"html"},
    {id:"color-converter",name:"Color Converter",category:"Color",icon:"◉",description:"Convert HEX colors into RGB and HSL values.",kind:"color"},
    {id:"password",name:"Secure Password Generator",category:"Security",icon:"⌑",description:"Generate a random password locally using your device's secure random generator.",kind:"password"},
    {id:"image-info",name:"Image Information",category:"Image",icon:"▧",description:"Inspect an image's dimensions, format, and file size without uploading it.",kind:"image"}
  ];
  const $ = id => document.getElementById(id);
  let activeCategory = "All", activeTool = null;
  const categories = ["All", ...new Set(tools.map(t => t.category))];
  $("tool-count").textContent = tools.length;
  function renderCategories() {
    $("categories").innerHTML = categories.map(c => '<button class="category-button '+(activeCategory===c?'active':'')+'" data-category="'+c+'">'+c+'</button>').join("");
    $("categories").querySelectorAll("button").forEach(b => b.addEventListener("click", () => { activeCategory=b.dataset.category; renderCategories(); renderTools(); }));
  }
  function renderTools() {
    const query = $("search").value.trim().toLowerCase();
    const list = tools.filter(t => (activeCategory==="All" || t.category===activeCategory) && (t.name+" "+t.description+" "+t.category).toLowerCase().includes(query));
    $("result-count").textContent = list.length+" shown";
    $("empty-state").hidden = list.length>0;
    $("tool-grid").innerHTML = list.map(t => '<button class="tool-card" data-tool="'+t.id+'"><span class="card-arrow">↗</span><span class="tool-icon">'+t.icon+'</span><h3>'+t.name+'</h3><p>'+t.description+'</p></button>').join("");
    $("tool-grid").querySelectorAll("[data-tool]").forEach(b=>b.addEventListener("click",()=>openTool(b.dataset.tool)));
  }
  function openTool(id) {
    activeTool=tools.find(t=>t.id===id); if(!activeTool)return;
    $("tool-panel").hidden=false; $("panel-icon").textContent=activeTool.icon; $("panel-category").textContent=activeTool.category.toUpperCase(); $("panel-title").textContent=activeTool.name; $("panel-description").textContent=activeTool.description; $("tool-message").textContent=""; $("tool-output-wrap").hidden=true;
    const fileTool=activeTool.kind==="image";
    const input = fileTool ? '<label class="control-label" for="tool-file">Choose an image (processed on this device)</label><input class="control-input" id="tool-file" type="file" accept="image/*">' : '<label class="control-label" for="tool-input">Input</label><textarea class="control-textarea" id="tool-input" placeholder="Type or paste your text here…"></textarea>';
    let options="";
    if(activeTool.kind==="case") options='<label class="control-label" for="case-mode">Conversion</label><select id="case-mode" class="control-select"><option value="upper">UPPERCASE</option><option value="lower">lowercase</option><option value="title">Title Case</option><option value="sentence">Sentence case</option><option value="toggle">tOGGLE cASE</option></select>';
    if(activeTool.kind==="reverse") options='<label class="control-label" for="reverse-mode">Reverse</label><select id="reverse-mode" class="control-select"><option value="chars">Characters</option><option value="lines">Line order</option></select>';
    if(activeTool.kind==="base64") options='<label class="control-label" for="base64-mode">Action</label><select id="base64-mode" class="control-select"><option value="encode">Encode</option><option value="decode">Decode</option></select>';
    if(activeTool.kind==="url") options='<label class="control-label" for="url-mode">Action</label><select id="url-mode" class="control-select"><option value="encode">Encode</option><option value="decode">Decode</option></select>';
    if(activeTool.kind==="html") options='<label class="control-label" for="html-mode">Action</label><select id="html-mode" class="control-select"><option value="escape">Escape HTML</option><option value="unescape">Unescape HTML</option></select>';
    if(activeTool.kind==="sort") options='<label class="control-label"><input id="sort-desc" type="checkbox"> Descending order</label><label class="control-label"><input id="sort-ci" type="checkbox" checked> Ignore letter case</label>';
    if(activeTool.kind==="dedupe") options='<label class="control-label"><input id="dedupe-trim" type="checkbox" checked> Ignore surrounding spaces</label><label class="control-label"><input id="dedupe-ci" type="checkbox"> Ignore letter case</label>';
    if(activeTool.kind==="password") options='<div class="control-row"><div><label class="control-label" for="pw-length">Length (8–128)</label><input class="control-input" id="pw-length" type="number" min="8" max="128" value="20"></div><div><label class="control-label" for="pw-count">How many (1–10)</label><input class="control-input" id="pw-count" type="number" min="1" max="10" value="1"></div></div><label class="control-label"><input id="pw-symbols" type="checkbox" checked> Include symbols</label>';
    $("tool-controls").innerHTML=input+options+'<button class="primary-button" id="run-tool">'+(fileTool?"Inspect image":"Run tool")+' →</button>';
    $("run-tool").addEventListener("click",runTool);
    $("tool-panel").scrollIntoView({behavior:matchMedia("(prefers-reduced-motion: reduce)").matches?"auto":"smooth",block:"start"});
  }
  function runTool() {
    const t=activeTool; let result="";
    try {
      if(t.kind==="image") { const f=$("tool-file").files[0]; if(!f)throw Error("Choose an image first."); const img=new Image(); const url=URL.createObjectURL(f); img.onload=()=>{showOutput("Name: "+f.name+"\nType: "+(f.type||"Unknown")+"\nSize: "+f.size.toLocaleString()+" bytes ("+(f.size/1024).toFixed(1)+" KB)\nDimensions: "+img.naturalWidth+" × "+img.naturalHeight+" px");URL.revokeObjectURL(url)};img.onerror=()=>{URL.revokeObjectURL(url);$("tool-message").textContent="This file could not be read as an image."};img.src=url;return; }
      if(t.kind==="password") { const length=Math.max(8,Math.min(128,Number($("pw-length").value)||20)), count=Math.max(1,Math.min(10,Number($("pw-count").value)||1)); let chars="ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";if($("pw-symbols").checked)chars+="!@#$%^&*()-_=+[]{}:,.?";const bytes=new Uint32Array(length*count);crypto.getRandomValues(bytes);result=Array.from({length:count},(_,i)=>Array.from({length},(_,j)=>chars[bytes[i*length+j]%chars.length]).join("")).join("\n");showOutput(result);return; }
      const value=$("tool-input")?.value ?? "";
      switch(t.kind) {
        case "case": {const mode=$("case-mode").value;result=mode==="upper"?value.toUpperCase():mode==="lower"?value.toLowerCase():mode==="title"?value.toLowerCase().replace(/\b([\p{L}\p{N}])/gu,m=>m.toUpperCase()):mode==="sentence"?value.toLowerCase().replace(/(^\s*|[.!?]\s+)([\p{L}])/gu,(m,a,b)=>a+b.toUpperCase()):Array.from(value,c=>c===c.toUpperCase()?c.toLowerCase():c.toUpperCase()).join("");break;}
        case "counter": {const words=value.trim()?value.trim().split(/\s+/u).length:0, chars=Array.from(value).length, sentences=(value.match(/[^.!?]+[.!?]+|[^.!?]+$/g)||[]).filter(s=>s.trim()).length;result="Words: "+words+"\nCharacters (with spaces): "+value.length+"\nCharacters (without spaces): "+value.replace(/\s/g,"").length+"\nLines: "+(value.length?value.split(/\r\n|\n|\r/).length:0)+"\nSentences (approx.): "+sentences+"\nEstimated reading time: "+Math.max(1,Math.ceil(words/200))+" min";break;}
        case "dedupe": {const seen=new Set();result=value.split(/\r\n|\n|\r/).filter(line=>{const key=($("dedupe-trim").checked?line.trim():line);const norm=$("dedupe-ci").checked?key.toLocaleLowerCase():key;if(seen.has(norm))return false;seen.add(norm);return true;}).join("\n");break;}
        case "sort": result=value.split(/\r\n|\n|\r/).sort((a,b)=>a.localeCompare(b,undefined,{numeric:true,sensitivity:$("sort-ci").checked?"base":"variant"})).join("\n");if($("sort-desc").checked)result=result.split("\n").reverse().join("\n");break;
        case "reverse": result=$("reverse-mode").value==="lines"?value.split(/\r\n|\n|\r/).reverse().join("\n"):Array.from(value).reverse().join("");break;
        case "json": result=JSON.stringify(JSON.parse(value),null,2);break;
        case "base64": result=$("base64-mode").value==="encode"?btoa(unescape(encodeURIComponent(value))):decodeURIComponent(escape(atob(value.trim())));break;
        case "url": result=$("url-mode").value==="encode"?encodeURIComponent(value):decodeURIComponent(value);break;
        case "html": if($("html-mode").value==="escape")result=value.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#39;");else {const doc=new DOMParser().parseFromString(value,"text/html");result=doc.documentElement.textContent;}break;
        case "color": result=colorInfo(value.trim());break;
        default: throw Error("This tool is not implemented yet.");
      }
      showOutput(result);
    } catch(e) { $("tool-output-wrap").hidden=true;$("tool-message").textContent="Could not process input: "+(e.message||"invalid input"); }
  }
  function colorInfo(hex){hex=hex.replace(/^#/,"");if(/^[0-9a-f]{3}$/i.test(hex))hex=hex.split("").map(c=>c+c).join("");if(!/^[0-9a-f]{6}$/i.test(hex))throw Error("Enter a HEX color like #8b5cf6.");const n=parseInt(hex,16),r=n>>16,g=(n>>8)&255,b=n&255;const max=Math.max(r,g,b)/255,min=Math.min(r,g,b)/255,d=max-min;let h=0,s=0,l=(max+min)/2;if(d){s=d/(1-Math.abs(2*l-1));switch(max){case r/255:h=60*(((g-b)/255/d)%6);break;case g/255:h=60*((b-r)/255/d+2);break;default:h=60*((r-g)/255/d+4)}}if(h<0)h+=360;return "HEX: #"+hex.toUpperCase()+"\nRGB: rgb("+r+", "+g+", "+b+")\nHSL: hsl("+Math.round(h)+", "+Math.round(s*100)+"%, "+Math.round(l*100)+"%)";}
  function showOutput(text){$("tool-output").value=text;$("tool-output-wrap").hidden=false;$("tool-message").textContent="Done — processed locally on your device."}
  $("close-tool").addEventListener("click",()=>{$("tool-panel").hidden=true;activeTool=null});
  $("copy-output").addEventListener("click",async()=>{try{await navigator.clipboard.writeText($("tool-output").value);$("tool-message").textContent="Copied to clipboard."}catch{$("tool-output").focus();$("tool-output").select();$("tool-message").textContent="Select the output and copy it."}});
  $("search").addEventListener("input",renderTools);
  $("theme-toggle").addEventListener("click",()=>{document.body.classList.toggle("light");localStorage.setItem("omni-theme",document.body.classList.contains("light")?"light":"dark")});
  if(localStorage.getItem("omni-theme")==="light")document.body.classList.add("light");
  document.addEventListener("keydown",e=>{if(e.key==="/"&&!["INPUT","TEXTAREA"].includes(document.activeElement.tagName)){e.preventDefault();$("search").focus()}if(e.key==="Escape"&&!$("tool-panel").hidden)$("tool-panel").hidden=true});
  renderCategories();renderTools();
})();