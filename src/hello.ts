module hello {
    "use strict";

    export interface Option<T> {
        get(): T;
        getOrElse(cb: () => T): T;
        size(): number;
        isEmpty(): boolean;
        nonEmpty(): boolean;

        map<B>(f: (e:T) => B): Option<B>;
        flatMap<B>(f: (e:T) => Option<B>): Option<B>;
        filter(f: (e:T) => boolean): Option<T>;
    }

    export class Some<T> implements Option<T> {
        constructor(public value: T) {}

        get():T {
            return this.value;
        }

        getOrElse(cb:() => T): T {
            return this.value;
        }

        size():number {
            return 1;
        }

        isEmpty():boolean {
            return false;
        }

        nonEmpty():boolean {
            return true;
        }

        map<B>(f: (e:T) => B): Option<B> {
            return new Some(f(this.value));
        }

        flatMap<B>(f: (e:T) => Option<B>): Option<B> {
            return f(this.value);
        }

        filter(f: (e:T) => boolean): Option<T> {
            if (f(this.value))
                return this;
            else
                return new None<T>();
        }
    }

    export class None<T> implements Option<T> {
        constructor() {}

        get():T {
            throw new Error("None.get");
        }

        getOrElse(cb:() => T):T {
            return cb();
        }

        size():number {
            return 0;
        }

        isEmpty():boolean {
            return true;
        }

        nonEmpty():boolean {
            return false;
        }

        map<B>(f: (e:T) => B): Option<B> {
            return new None<B>();
        }

        flatMap<B>(f: (e:T) => Option<B>): Option<B> {
            return new None<B>();
        }

        filter(f: (e:T) => boolean): Option<T> {
            return this;
        }
    }

    export var Optional = {
        of: function <T>(value: T): Option<T> {
            if (value === null || value === undefined)
                return new None<T>();
            else
                return new Some<T>(value);
        }
    }

    var test = Optional.of(12);
    var test2 = Optional.of(undefined);

    var test3: Option<number> = test.map(x => x + 1);

    var optionOfOption = Optional.of(Optional.of(12));
    var flattened: Option<number> = optionOfOption.flatMap(x => x);
}
