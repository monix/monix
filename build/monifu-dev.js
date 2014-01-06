///<reference path='reference' />
var monifu;
(function (monifu) {
    "use strict";

    var Option = (function () {
        function Option(value) {
            this.value = value;
        }
        Option.prototype.get = function () {
            if (this.value != undefined)
                return this.value;
            else
                throw new TypeError("None.get");
        };

        Option.prototype.getOrElse = function (cb) {
            if (this.isEmpty())
                return cb();
            else
                return this.value;
        };

        Option.prototype.isEmpty = function () {
            return this.value == undefined;
        };

        Option.prototype.nonEmpty = function () {
            return !this.isEmpty();
        };

        Option.prototype.size = function () {
            return this.isEmpty() ? 0 : 1;
        };

        Option.prototype.map = function (f) {
            if (this.isEmpty())
                return this;
            else
                return new Option(f(this.value));
        };

        Option.prototype.flatMap = function (f) {
            if (this.isEmpty())
                return this;
            else
                return f(this.value);
        };

        Option.prototype.filter = function (f) {
            if (this.nonEmpty && f(this.value))
                return this;
            else
                return new Option(null);
        };

        Option.prototype.foreach = function (f) {
            if (this.nonEmpty())
                f(this.value);
        };

        Option.prototype.foldLeft = function (initial) {
            return function (folder) {
                if (this.isEmpty())
                    return initial;
                else
                    return folder(initial, this.value);
            };
        };

        Option.of = function (value) {
            return new Option(value);
        };

        Option.some = function (value) {
            if (value == undefined)
                throw new TypeError("value cannot be undefined");
            return new Option(value);
        };

        Option.none = function () {
            return new Option(null);
        };
        return Option;
    })();
    monifu.Option = Option;
})(monifu || (monifu = {}));
///<reference path="option" />
var monifu;
(function (monifu) {
    var a = monifu.Option.of(3);
    var b = a.map(function (x) {
        return (x + 1) + "";
    });
})(monifu || (monifu = {}));
//# sourceMappingURL=monifu-dev.js.map
