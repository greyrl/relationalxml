// data transmission tools

jQuery.perstools = {

    xmlGen : new jQuery.dom.XmlGen(),

    // load data from the persistence mechanism and replace an element
    load : function(url, element, resultQuery, _function) {
        this.log("load", "url [" + url + "]");
        this.log("load", "result query [" + resultQuery + "]");
        $.ajax({
            url: jQuery.trim(url),
            type: "GET",
            success: function(response, ioArgs) { 
                var q = resultQuery;
                var op = "load";
                var r = jQuery.dom._replaceElement(op, response, element, q);
                if (_function) _function(r);
                return r;
            },
            error: this._handleXhrError,
            cache: false
        });
    }, 

    // the complete save method
    coresave : function(config) {
        var async = ! config.sync;
        var _class = this.xmlGen._complexCls;
        var saves = new Array();
        var xmlg = this.xmlGen;
        if (! config.errorFunc) config.errorFunc = this._handleXhrError;
        $(config.element).firstLevel("." + _class).each(function() {
            var result = xmlg.generate(this);
            if (result) saves.push(result);
        });
        if (saves.length == 0) {
            $.log("WARNING!!! html block produced no XML");
            return;
        } else this.log("save", "save " + saves.length);
        var xmlStr = "";
        jQuery.each(saves, function() {
            var next = jQuery.dom.innerXML(this);
            if (next) xmlStr += next;
        });
        xmlStr = jQuery.base64.utf8Encode(xmlStr);
        this.log("save", "finished generation " + xmlStr);
        $.ajax({
            async: async,
            url: jQuery.trim(config.url),
            type: "POST",
            data: { xml : jQuery.base64.encode(xmlStr, "UTF-8") },
            success: function(data) { 
                var rq = config.resultQuery;
                var r = config.loadFunc("save", data, config.wrap, rq);
                if (config.postFunc) config.postFunc(r);
                return r;
            },
            error: config.errorFunc,
            cache: false
        });
    },

    // save and update a wrapper 
    save : function(url, element, wrap, query, _function) {
        this.coresave({
            url: url, 
            element: element, 
            wrap: wrap, 
            resultQuery: query, 
            postFunc: _function, 
            loadFunc: jQuery.dom._updateWrapper
        });
    },

    // save and replace an element
    saveAndReplace : function(url, element, query, _function) {
        this.coresave({
            url: url, 
            element: element, 
            wrap: element, 
            resultQuery: query, 
            postFunc: _function, 
            loadFunc: jQuery.dom._replaceElement
        });
    },

    // update ids from an ajax save with error option
    saveAndUpdateE : function(url, element, query, postFunc, error, sync) {
        this.coresave({
            url: url, 
            element: element, 
            resultQuery: query, 
            loadFunc: this._updateIds,
            postFunc: postFunc, 
            errorFunc: error,
            sync: sync,
            wrap: element
        });
    },

    // update ids from an ajax save
    saveAndUpdate : function(url, element, query, postFunction, sync) {
        this.coresave({
            url: url, 
            element: element, 
            resultQuery: query, 
            loadFunc: this._updateIds,
            postFunc: postFunction, 
            sync: sync,
            wrap: element
        });
    },
 
    // dynamically generate additional html 
    generator : function(config) {
        if (! config.error) config.error = this._handleXhrError;
        var xmlStr = this.xmlGen.generate($(config.data)[0]);
        xmlStr = jQuery.dom.innerXML(xmlStr);
        $.ajax({
            url: jQuery.trim(config.url),
            type: "POST",
            data: { xml : jQuery.base64.encode(xmlStr, "UTF-8") },
            success: function(data) { 
                var m = jQuery.dom._replaceElement;
                var r = m("_generate", data, $(config.replace), config.query);
                if (config._function) config._function(r);
            },
            error: config.error,
            cache: false
        });
    },

    // convenience
    generate : function(url, data, replace, query, _function) {
        this.generator({
            url: url, 
            data: data, 
            replace: replace, 
            query: query, 
            _function: _function
        });
    },

    // set the error handling element
    setErrorElement : function() {
        jQuery.dom.errEle = this;
    },

    // enable or disable an elmenet
    switchEnable : function(element) {
        if (! element || element.nodeType != jQuery.dom.ELEMENT_NODE) return;
        if (element.disabled) jQuery.enableButton(element);
        else jQuery.disableButton(element);
    },

    // convenience logging for tools
    log : function(method, message) {
        $.log("jQuery.perstools." + method + "() : " + message);
    },

    // replace characters in an attribute
    replaceAttVal : function(query, att, old, _new) {
        query = $(query);
        jQuery.perstools.log("replace", "check " + query.size());
        query.each(function() {
            var val = $(this).attr(att);
            if (! val) return;
            for (var x = 0 ; old[x] && _new[x]; x++) {
                if (val.replace) val = val.replace(old[x],_new[x]);
            }
            $(this).attr(att, val);
        });
    },

    // extract form selections from an element 
    getAtts : function(query, keys) {
        var result = [];
        if (! query || ! keys) return result;
        $(query).each(function() {
            var next = [];
            for (var x = 0 ; x < keys.length ; x++) {
                next[x] = [keys[x], $(this).attr(keys[x])];
            }
            result.push(next);
        });
        return result;
    },

    // set form selections from an extraction 
    setAtts : function(query, values) {
        $(query).each(function(count) {
            var input = this;
            jQuery.each(values[count], function() {
                if (! this[1]) return;
                $(input).attr(this[0], this[1]);
            });
        });
    },

    // update ids after a save
    // TODO elements must be sequential, make it work with "insert"
    _updateIds : function(key, data, wrap, resultQuery) {
        data = $("[id]", $(resultQuery, data).parent());
        var count = 0;
        $("[name=id]", wrap).each(function() {
            var disabled = $(this).parents(".disabled").size();
            if (disabled) return;
            var id = $(data[count]).attr("id");
            //var val = $(this).val();
            //$.log("replace " + val + " with " + id);
            $(this).val(id);
            count++;
        });
        var savesize = data.size(); 
        $.log("_updateIds() : updated " + count + " from " + savesize);
    },
                
    // SAVE SOMETHING BACK TO THE DATABASE
    // handle an error on a request
    _handleXhrError : function(XMLHttpRequest, textStatus, errorThrown) {
        var code = "HTTP status code: " + XMLHttpRequest.status; 
        jQuery.perstools.log("_handleXhrError", "AJAX operation failed");
        jQuery.perstools.log("_handleXhrError", code);
    }

}
