jQuery.log = function(msg) {
    if (typeof console == "object") console.log("%s", msg);
    return this;
};

jQuery.fn.log = function(message) {
    return this.each(function() {
        jQuery.log(message + this);
    });
};

jQuery.tlog = function(msg, timestamp) {
    var now = new Date().getTime();
    if (! timestamp) timestamp = now;
    if ((typeof console == "object") && timestamp) {
        console.log("%s, timing %d", msg, now - timestamp);
    }
    return now;
};
