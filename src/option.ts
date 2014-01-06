///<reference path='reference' />

module monifu {
  "use strict";

  export class Option<T> implements Iterable<T>, Foldable<T>, Monad<T> {
    constructor (private value: T) {}

    get(): T {
      if (this.value != undefined)
        return this.value
      else
        throw new TypeError("None.get");
    }

    getOrElse(cb: () => T): T {
      if (this.isEmpty())
        return cb();
      else
        return this.value;
    }

    isEmpty(): boolean {
      return this.value == undefined;
    }

    nonEmpty(): boolean {
      return !this.isEmpty();
    }

    size(): number {
      return this.isEmpty() ? 0 : 1;
    }

    map<B>(f: (e:T) => B): Option<B> {
      if (this.isEmpty())
        return <Option<B>><any>this;
      else
        return new Option(f(this.value));
    }

    flatMap<B>(f: (e:T) => Option<B>): Option<B> {
      if (this.isEmpty())
        return <Option<B>><any>this;
      else
        return f(this.value);
    }

    filter(f: (e:T) => boolean): Option<T> {
      if (this.nonEmpty && f(this.value))
        return this;
      else
        return new Option<T>(null);
    }

    foreach(f: (e:T) => void) {
      if (this.nonEmpty())
        f(this.value);        
    }

    foldLeft<R>(initial: R): (folder: (result: R, elem: T) => R) => R {
      return function (folder: (result: R, elem: T) => R) {
        if (this.isEmpty())
          return initial;
        else
          return folder(initial, this.value);
      };
    }

    static of<A>(value: A): Option<A> {
      return new Option(value);
    }

    static some<A>(value: A): Option<A> {
      if (value == undefined)
        throw new TypeError("value cannot be undefined");
      return new Option(value);
    }

    static none<A>(): Option<A> {
      return new Option<A>(null);
    }
  }
}