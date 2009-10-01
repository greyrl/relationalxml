// copied from here:
// http://www.webtoolkit.info/javascript-base64.html

jQuery.base64 = {

    _keyStr : 
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=",
    
    // encode a base64 string
    encode : function(str, charset) {
        if (! str) return;
        var result = "";
        var chr1, chr2, chr3, enc1, enc2, enc3, enc4;
        var i = 0;
        var _keyStr = this._keyStr;
        while (i < str.length) {
            chr1 = str.charCodeAt(i++);
            chr2 = str.charCodeAt(i++);
            chr3 = str.charCodeAt(i++);
            enc1 = chr1 >> 2;
            enc2 = ((chr1 & 3) << 4) | (chr2 >> 4);
            enc3 = ((chr2 & 15) << 2) | (chr3 >> 6);
            enc4 = chr3 & 63;
            if (isNaN(chr2)) enc3 = enc4 = 64;
            else if (isNaN(chr3)) enc4 = 64;
            result += _keyStr.charAt(enc1) + _keyStr.charAt(enc2) +
                _keyStr.charAt(enc3) + _keyStr.charAt(enc4);
        }
        if (! charset) charset = "UTF-8";
        return "=?" + charset + "?B?" + result + "?=";
    },

    // decode a base64 string
    decode : function(str) {
        if (! str) return;
        var result = "";
        var chr1, chr2, chr3;
        var enc1, enc2, enc3, enc4;
        var i = 0;
        var _keyStr = this._keyStr;
        var utf8index = str.indexOf("?B?");
        if (str.substring(0, 2) == "=?" && utf8index > -1) {
            // remove the rfc1522 components
            utf8index += 3;
            str = str.substring(utf8index, str.length);
            str = str.substring(0, str.length - 2);
        }
        str = str.replace(/[^A-Za-z0-9\+\/\=]/g, "");
        while (i < str.length) {
            enc1 = _keyStr.indexOf(str.charAt(i++));
            enc2 = _keyStr.indexOf(str.charAt(i++));
            enc3 = _keyStr.indexOf(str.charAt(i++));
            enc4 = _keyStr.indexOf(str.charAt(i++));
            chr1 = (enc1 << 2) | (enc2 >> 4);
            chr2 = ((enc2 & 15) << 4) | (enc3 >> 2);
            chr3 = ((enc3 & 3) << 6) | enc4;
            result = result + String.fromCharCode(chr1);
            if (enc3 != 64) result += String.fromCharCode(chr2);
            if (enc4 != 64) result += String.fromCharCode(chr3);
        }
        return result;
    },

    // encode a string in UTF-8
    utf8Encode : function(str) {
        if (! str) return;
        str = str.replace(/\r\n/g,"\n");
        var result = "";
        for (var n = 0; n < str.length; n++) {
            var c = str.charCodeAt(n);
            if (c < 128) result += String.fromCharCode(c);
            else if ((c > 127) && (c < 2048)) {
                result += String.fromCharCode((c >> 6) | 192);
                result += String.fromCharCode((c & 63) | 128);
            } else {
                result += String.fromCharCode((c >> 12) | 224);
                result += String.fromCharCode(((c >> 6) & 63) | 128);
                result += String.fromCharCode((c & 63) | 128);
            }
        }
        return result;
    },

    // decode a string from UTF-8
    utf8Decode : function(str) {
        if (! str) return;
        var result = "";
        var i = 0;
        var c = c1 = c2 = 0;
        while (i < str.length) {
            c = str.charCodeAt(i);
            if (c < 128) {
                result += String.fromCharCode(c);
                i++;
            } else if ((c > 191) && (c < 224)) {
                c2 = str.charCodeAt(i+1);
                result += String.fromCharCode(((c & 31) << 6) | (c2 & 63));
                i += 2;
            } else {
                c2 = str.charCodeAt(i+1);
                c3 = str.charCodeAt(i+2);
                result += String.fromCharCode(
                        ((c & 15) << 12) | ((c2 & 63) << 6) | (c3 & 63));
                i += 3;
            }
        }
        return result;
    }

}
