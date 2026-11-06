package com.lists.app

private fun jsDateNow(): Double = js("Date.now()")
fun currentTimeMillis(): Long = jsDateNow().toLong()

private fun jsFormatEpochToIsoDate(millis: Double): String = js("new Date(millis).toISOString().split('T')[0]")
fun formatEpochToIsoDate(millis: Long): String = try { jsFormatEpochToIsoDate(millis.toDouble()) } catch (_: Throwable) { "" }

private fun jsFormatDateTime(millis: Double): String = js("new Date(millis).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' })")
fun formatEpochToPrettyDateTime(millis: Long): String = try { jsFormatDateTime(millis.toDouble()) } catch (_: Throwable) { "" }

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
    input.accept = '.listspkg,.zip,.json';
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

fun jsFetchPackageManifest(downloadUrl: String, token: String, onSuccess: (String) -> Unit, onError: (String) -> Unit): Unit = js("""{
    fetch(downloadUrl, {
        headers: { "Authorization": "Bearer " + token }
    }).then(function(res) {
        if (!res.ok) throw new Error("HTTP " + res.status);
        return res.arrayBuffer();
    }).then(function(buf) {
        if (window.JSZip) {
            return window.JSZip.loadAsync(buf).then(function(zip) {
                window.__lastDownloadedZip = zip;
                var manifest = zip.file("manifest.json");
                if (!manifest) {
                    var matches = zip.file(/manifest\.json$/i);
                    if (matches && matches.length > 0) manifest = matches[0];
                }
                if (!manifest) {
                    var jsonFiles = zip.file(/\.json$/i);
                    if (jsonFiles && jsonFiles.length > 0) manifest = jsonFiles[0];
                }
                if (manifest) {
                    return manifest.async("string");
                }
                throw new Error("No manifest.json found in archive");
            });
        }
        var decoder = new TextDecoder();
        return decoder.decode(buf);
    }).then(function(str) {
        onSuccess(str);
    }).catch(function(err) {
        console.error("fetchPackageManifest error:", err);
        onError(err && err.message ? err.message : String(err));
    });
}""")

fun jsUploadPackageToDrive(uploadUrl: String, token: String, manifestJson: String, onSuccess: (String) -> Unit, onError: (String) -> Unit): Unit = js("""{
    if (window.JSZip) {
        var zip = window.__lastDownloadedZip || new window.JSZip();
        zip.file("manifest.json", manifestJson);
        zip.generateAsync({ type: "blob", compression: "DEFLATE", compressionOptions: { level: 1 } }).then(function(blob) {
            return fetch(uploadUrl, {
                method: "PATCH",
                headers: {
                    "Authorization": "Bearer " + token,
                    "Content-Type": "application/octet-stream"
                },
                body: blob
            });
        }).then(function(res) {
            if (res.ok) {
                return res.json().then(function(data) {
                    onSuccess(data && data.modifiedTime ? data.modifiedTime : "");
                }).catch(function() {
                    onSuccess("");
                });
            } else {
                onError("Upload package failed: " + res.status);
            }
        }).catch(function(err) {
            onError(err && err.message ? err.message : String(err));
        });
    } else {
        onError("JSZip not available");
    }
}""")

fun jsHasLastDownloadedZip(): Boolean = js("""(function() {
    return !!window.__lastDownloadedZip;
})()""")

suspend fun fetchPackageManifest(downloadUrl: String, token: String): String =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        jsFetchPackageManifest(downloadUrl, token,
            onSuccess = { if (cont.isActive) cont.resumeWith(Result.success(it)) },
            onError = { if (cont.isActive) cont.resumeWith(Result.failure(Exception(it))) }
        )
    }

suspend fun uploadPackageToDrive(uploadUrl: String, token: String, manifestJson: String): String? =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        jsUploadPackageToDrive(uploadUrl, token, manifestJson,
            onSuccess = { if (cont.isActive) cont.resumeWith(Result.success(it)) },
            onError = { if (cont.isActive) cont.resumeWith(Result.success(null)) }
        )
    }

fun jsGetCoverBlobUrl(uuid: String): String? = js("""(function() {
    if (!uuid || !window.__lastDownloadedZip) return null;
    window.__coverBlobCache = window.__coverBlobCache || {};
    if (window.__coverBlobCache[uuid]) return window.__coverBlobCache[uuid];

    var zip = window.__lastDownloadedZip;
    var file = zip.file("covers/" + uuid + ".webp") ||
               zip.file(uuid + ".webp") ||
               zip.file("covers/" + uuid + ".jpg") ||
               zip.file("covers/" + uuid + ".png");
    if (!file) return null;

    file.async("blob").then(function(blob) {
        var url = URL.createObjectURL(blob);
        window.__coverBlobCache[uuid] = url;
    });
    return window.__coverBlobCache[uuid] || null;
})()""")



