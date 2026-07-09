<?php
// A tiny script to test the run configuration and highlighting.
$name = "world";
echo "Hello, {$name}!\n";

for ($i = 1; $i <= 3; $i++) {
    echo "line {$i}\n";
}

/* block comment */
$sum = 0;
foreach ([1, 2, 3, 0xFF] as $n) {
    $sum += $n;
}
echo "sum = {$sum}\n";
