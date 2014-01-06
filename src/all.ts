///<reference path="option" />

module monifu {
  var a: Option<number> = Option.of(3);
  var b: Option<string> = a.map(x => (x + 1) + "")
}
