// get the value of CDATA nodes for an element
jQuery.getCDATAValue = function(element) {
    var result = "";
    jQuery.each(element.childNodes, function() {
        if (this.nodeType == 4) result += this.nodeValue;
    });
    return result;
};

// get HTTPXMLRequest results    
jQuery.getResponseChild = function(httpreq) {
    if (httpreq && httpreq.responseXML && httpreq.responseXML.firstChild) 
        return httpreq.responseXML.firstChild;
    else return null;
};

// don't know why this isn't in jQuery...
jQuery.enableButton = function(element) {
    if (! element) return;
    $(element).attr("disabled", "");
};

// don't know why this isn't in jQuery...
jQuery.disableButton = function(element) {
    if (! element) return;
    $(element).attr("disabled", true);
};

// don't know why this isn't in jQuery...
jQuery.enableCheck = function(element) {
    if (! element) return;
    $(element).attr("checked", "checked")[0].checked = true;
};

// don't know why this isn't in jQuery...
jQuery.disableCheck = function(element) {
    if (! element) return;
    $(element).attr("checked", "")[0].checked = false;
};

// is this a numeric value
jQuery.isNumeric = function(test) {
    return test.match(/^\d+$/) != null;
} 

// bind an object to a function
jQuery.hitch = function(obj, _function) {
    return function() {
        _function.apply(obj, arguments);
    }
}

// increment global counter
jQuery.incrementer = function(key, readonly) {
    var count = jQuery.data(document.body, key);
    if (readonly) return count;
    count = count ? count + 1 : 1;
    jQuery.data(document.body, key, count);
    return count;
}

// reset global counter
jQuery.resetincrementer = function(key) {
    jQuery.data(document.body, key, 0);
}

// verify an email address
jQuery.validemail = function(email) {
    var pattern = "^[A-Z,a-z,., ,',]*[<]?[A-Z,a-z,0-9,.,_,%,+,-]+";
    pattern += "@[A-Z,a-z,0-9,.,-]+\.[A-Z,a-z]{2,6}[>]?$";
    return email.match(new RegExp(pattern));
}
