package com.lists.app

private fun jsDateNow(): Double = js("Date.now()")
fun currentTimeMillis(): Long = jsDateNow().toLong()

private fun jsEncodeURIComponent(s: String): String = js("encodeURIComponent(s)")
fun encodeURIComponent(s: String): String = jsEncodeURIComponent(s)

private fun jsVibrate(ms: Int): Unit = js("{ if (window.navigator && window.navigator.vibrate) { window.navigator.vibrate(ms); } }")

fun triggerStrongTick(context: Any? = null) {
    try {
        jsVibrate(45)
    } catch (_: Throwable) {}
}

fun triggerAsmrTick(context: Any? = null) {
    try {
        jsVibrate(25)
    } catch (_: Throwable) {}
}

fun jsDownloadFile(filename: String, content: String): Unit = js("""{
    const a = document.createElement('a');
    a.href = 'data:text/json;charset=utf-8,' + encodeURIComponent(content);
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}""")

fun getFileReaderResult(reader: org.w3c.files.FileReader): String = js("String(reader.result || '')")

fun jsDownloadPackage(filename: String, jsonContent: String): Unit = js("""{
    if (window.JSZip) {
        var zip = new window.JSZip();
        zip.file("manifest.json", jsonContent);
        zip.generateAsync({ type: "blob", compression: "DEFLATE" }).then(function(blob) {
            var url = URL.createObjectURL(blob);
            var a = document.createElement('a');
            a.href = url;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            setTimeout(function() { URL.revokeObjectURL(url); }, 1500);
        });
    } else {
        var blob = new Blob([jsonContent], { type: "application/octet-stream" });
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(function() { URL.revokeObjectURL(url); }, 1500);
    }
}""")

fun jsPickAndReadPackage(onSuccess: (String) -> Unit, onError: (String) -> Unit): Unit = js("""{
    var input = document.createElement('input');
    input.type = 'file';
    input.accept = '.listspkg,.listypkg,.zip,.json';
    input.onchange = function() {
        var file = input.files && input.files[0];
        if (!file) return;
        var name = file.name.toLowerCase();
        if (name.endsWith('.json')) {
            var reader = new FileReader();
            reader.onload = function() {
                var text = String(reader.result || '');
                if (text) onSuccess(text);
                else onError('Empty file');
            };
            reader.onerror = function() { onError('Failed to read file'); };
            reader.readAsText(file);
        } else if (window.JSZip) {
            window.JSZip.loadAsync(file).then(function(zip) {
                var manifestEntry = zip.file("manifest.json");
                if (manifestEntry) {
                    return manifestEntry.async("string");
                }
                var jsonFiles = zip.file(/\.json$/i);
                if (jsonFiles && jsonFiles.length > 0) {
                    return jsonFiles[0].async("string");
                }
                throw new Error("No manifest.json found in archive");
            }).then(function(text) {
                if (text) onSuccess(text);
                else onError('Empty manifest in package');
            }).catch(function(err) {
                onError(err && err.message ? err.message : 'Failed to extract package');
            });
        } else {
            var reader = new FileReader();
            reader.onload = function() {
                var text = String(reader.result || '');
                if (text) onSuccess(text);
                else onError('Empty file');
            };
            reader.readAsText(file);
        }
    };
    input.click();
}""")

