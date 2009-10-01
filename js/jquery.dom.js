// xml dom management tools

jQuery.dom = {

    ELEMENT_NODE : 1,

    // generate XML from a block of html nodes
    XmlGen : Class.extend({

        doc: false,

        _altNameAtt: "altname",
        _attCls:  "att",
        _complexCls: "complex",
        _disabledCls:  "disabled",
        _insertCls: "insert",
        _nameAtt: "name",
        _primitiveCls: "primitive",

        init: function() {
            this.reset();
        },

        // process a single node that has child values
        generate: function(node, current) {
            if (! node || node.nodeType != jQuery.dom.ELEMENT_NODE) return false;
            if (! current) current = this.doc.firstChild;
            var add = this._addNode(node, current);
            if (add) current = add;
            if (! current) return false;
            jQuery.each(node.childNodes, jQuery.hitch(this, function(id, next) {
                var result = this._process(next, current);
                if (result && ! result[0]) current.appendChild(result);
                else jQuery.each(result, function(id, next) {
                    current.appendChild(next);
                });
            }));
            return add ? current : false;
        },

        // reset the generator
        reset: function() {
            if (this.doc) $(this.doc.firstChild).empty();
            this.doc = jQuery.dom.createDocument("<wrapper/>");
        },

        // build a new element if necessary
        _addNode: function(node, current) {
            if (! $(node).hasClass(this._complexCls)) return false;
            if ($(node).hasClass(this._disabledCls)) return false;
            var name = $(node).attr(this._nameAtt);
            if (! name) {
                var inner = node.parentNode.innerHTML; 
                throw "Node in block [" + inner + "] defined without name";
            }
            var _new = this.doc.createElement(name);
            current.appendChild(_new);
            return _new;
        },

        // process a child
        _process: function(node, current) {
            if (! node || node.nodeType != jQuery.dom.ELEMENT_NODE) return false;
            if ($(node).hasClass(this._disabledCls)) return false;
            // if there are children, recursively process
            if (jQuery.dom.hasChildren(node)) {
                var nname = $(node).attr("name");
                if (nname)
                    this._log("_process", "process children on [" + nname + "]");
                return this.generate(node, current);
            }
            // follow an insert
            if ($(node).hasClass(this._insertCls))
                return this._processInsert($(node).attr("name"));
            // allow for overriding name attribute
            var name = $(node).attr(this._altNameAtt);
            if (! name) name = $(node).attr(this._nameAtt);
            var value = this._value(node);
            if (! name || ! value) return false; 
            if ($(node).hasClass(this._primitiveCls)) {
                // this is an element
                this._log("_process", "process element [" + name + "]");
                var _new = this.doc.createElement(name);
                _new.appendChild(this.doc.createTextNode(value));
                return _new;
            } else if ($(node).hasClass(this._attCls)) {
                // this is an attribute
                this._log("_process", "process attribute [" + name + "]");
                current.setAttribute(name, value);
            }
            return false;
        },

        // grab a different element and add it here
        _processInsert: function(query) {
            if (! query) return false;
            this._log("_processInsert", "process insert [" + query + "]");
            var result = new Array();
            $(query).each(jQuery.hitch(this, function(id, insert) {
                var name = $(insert).attr("name");
                this._log("_process", "follow name [" + name + "]");
                var _new = this._process(insert, null);
                if (_new) result.push(_new);
            }));
            this._log("_processInsert", "finish process insert [" + query + "]");
            if (result.length > 0) return result;
            this._log("jQuery.dom.XmlGen._processInsert() : no results");
            return false;
        },

        // attempt to extract the xml body value from an element
        _value: function(node) {
            if (! node || node.nodeType != jQuery.dom.ELEMENT_NODE) return false;
            // value elements must not have children
            if (jQuery.dom.hasChildren(node)) return false;
            if (node.value) return node.value
            // return empty text nodes in firefox
            if (node.nodeName.toLowerCase() == "textarea") return node.value
            // since this isn't an input, assume text body is content
            return $(node).text();
        },

        // convenience
        _log: function(method, msg) {
            //$.log("jQuery.dom.XmlGen." + method + "() : " + msg);
        }

    }),

    // create a DOM object
    createDocument : function(str) {
        if (navigator.appName == 'Microsoft Internet Explorer') {
            var doc = new ActiveXObject('Microsoft.XMLDOM');
            doc.async = 'false'
            doc.loadXML(str);
            return doc;
        } return (new DOMParser()).parseFromString(str, 'text/xml');
    },

    // serialiaze an XML node
    innerXML : function(node) {
        if (! node) return node;
        if (node.innerXML) return node.innerXML;
        else if (node.xml) return node.xml;
        else if (typeof XMLSerializer != "undefined") {
            return (new XMLSerializer()).serializeToString(node);  
        }
    },

    // does this element have any children 
    hasChildren : function(node) {
        return (node && $(node).children().size() > 0);
    },

    // convenience logging for tools
    log : function(method, message) {
        $.log("jQuery.dom." + method + "() : " + message);
    },

    // update a wrapper with the content from an xhr request
    _updateWrapper : function(name, response, wrapper, query) {
        if (! wrapper || ! query) return false;
        query = jQuery.dom._eleFromQuery(name, response, query);
        if (query.length > 0) $(wrapper).empty().append(query);
        return wrapper; 
    },

    // replace an existing element
    _replaceElement : function(name, response, element, query) {
        if (! element || ! query) return false;
        query = jQuery.dom._eleFromQuery(name, response, query);
        if (query.length == 0) return element;
        $(element).replaceWith(query);
        return query; 
    },

    // convenience
    _eleFromQuery : function(name, response, query) {
        var newElement = $(document.createElement("div"));
        newElement.append(response);
        jQuery.dom._handleError(newElement);
        // the byId only looks elements on the existing DOM...
        query = $(query, newElement);
        jQuery.dom.log(name, "found " + query.length + " result(s)");
        if (query.length == 0) $.log(newElement[0].innerHTML);
        return query;
    },

    // handle an error, assuming one occurred
    _handleError : function(response) {
        var errEle = jQuery.dom.errEle;
        var id = $(errEle).attr("id");
        if (! errEle || ! id) return;
        var newErr = $("#" + id, response);
        //var newErr = jQuery.dom.findChildWithId(response, id);
        if (newErr.length == 0) return;
        jQuery.dom.log("_handleError", "found error id [" + id + "]");
        $(errEle).replaceWith(newErr);
        jQuery.dom.errEle = newErr;
    },

    _destroy: false,

    // remove an elemen with a container
    _destroyElement : function(node) {
        node = $(node);
        if (! this._destroy){
            this._destroy = $(document.createElement("div"));
            this._destroy.hide();
            $(document.body).append(this._destroy);
        }
        this._destroy.append(
            node.parentNode ? node.parentNode.removeChild(node) : node);
        this._destroy[0].innerHTML = "";
    }

}

// extract all children or current element on matches
jQuery.fn.firstLevel = function(selector) {

    return this.pushStack(jQuery.map(this, function(elem) {
        return jQuery.firstLevel(elem, selector);
    }));

};

jQuery.firstLevel = function(elem, selector) {

    elem = $(elem);
    var _parent = elem.filter(selector)[0];
    if (_parent) return _parent;
    else return jQuery.map(elem.children(), function(kid) {
        return jQuery.firstLevel(kid, selector);
    });

};
