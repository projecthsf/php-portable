<?php
/**
 * php-portable: project class-map generator.
 *
 * Projects without a Composer autoloader (e.g. swoole_system-style layouts with a custom
 * AutoloadService and symlinked checkouts) cannot be resolved by Phpactor's diagnostics
 * pipeline: its fallback class-to-file scanner neither follows symlinks nor understands
 * "many classes per file" / lowercase filenames. This script scans the project once
 * (following symlinks) and emits a Composer-compatible class map:
 *
 *   <out>/autoload.php                    (dummy, satisfies the path check)
 *   <out>/composer/autoload_classmap.php  (return array<string,string>)
 *
 * Usage: php classmap-generator.php <projectRoot> <outputDir>
 */

/**
 * @return array{classes: list<string>, protobuf: list<string>} declared class FQNs, and the
 * subset whose declaration extends \ProtobufMessage (the runtime-provided protobuf base —
 * its generated accessors are __call magic that static analysis cannot see).
 */
function extract_classes(string $src): array
{
    $tokens = @token_get_all($src);
    $namespace = '';
    $classes = [];
    $protobuf = [];
    $count = count($tokens);
    for ($i = 0; $i < $count; $i++) {
        $token = $tokens[$i];
        if (!is_array($token)) {
            continue;
        }
        if ($token[0] === T_NAMESPACE) {
            for ($j = $i + 1; $j < $count; $j++) {
                $t = $tokens[$j];
                if (is_array($t) && in_array($t[0], [T_STRING, T_NAME_QUALIFIED, T_NAME_FULLY_QUALIFIED], true)) {
                    $namespace = trim($t[1], '\\');
                    break;
                }
                if ($t === ';' || $t === '{') {
                    break;
                }
            }
            continue;
        }
        if (in_array($token[0], [T_CLASS, T_INTERFACE, T_TRAIT, T_ENUM], true)) {
            $fqn = null;
            for ($j = $i + 1; $j < $count; $j++) {
                $t = $tokens[$j];
                if (is_array($t)) {
                    if ($t[0] === T_STRING) {
                        $fqn = $namespace === '' ? $t[1] : $namespace . '\\' . $t[1];
                        break;
                    }
                    if ($t[0] !== T_WHITESPACE && $t[0] !== T_COMMENT && $t[0] !== T_DOC_COMMENT) {
                        break; // Foo::class, anonymous class, etc.
                    }
                } else {
                    break;
                }
            }
            if ($fqn === null) {
                continue;
            }
            $classes[] = $fqn;
            // look ahead for `extends ... ProtobufMessage` before the class body opens
            for ($j = $i + 1; $j < $count; $j++) {
                $t = $tokens[$j];
                if ($t === '{') {
                    break;
                }
                if (is_array($t) && $t[0] === T_EXTENDS) {
                    for ($k = $j + 1; $k < $count; $k++) {
                        $p = $tokens[$k];
                        if (is_array($p) && in_array($p[0], [T_STRING, T_NAME_QUALIFIED, T_NAME_FULLY_QUALIFIED], true)) {
                            if (ltrim($p[1], '\\') === 'ProtobufMessage') {
                                $protobuf[] = $fqn;
                            }
                            break;
                        }
                        if ($p === '{' || (is_array($p) && !in_array($p[0], [T_WHITESPACE, T_COMMENT, T_DOC_COMMENT], true))) {
                            break;
                        }
                    }
                    break;
                }
            }
        }
    }
    return ['classes' => $classes, 'protobuf' => $protobuf];
}

$root = rtrim($argv[1] ?? '', '/');
$out = rtrim($argv[2] ?? '', '/');
if ($root === '' || $out === '' || !is_dir($root)) {
    fwrite(STDERR, "usage: php classmap-generator.php <projectRoot> <outputDir>\n");
    exit(1);
}

$map = [];
$protobufClasses = [];
$declarationCounts = [];
$iterator = new RecursiveIteratorIterator(
    new RecursiveDirectoryIterator(
        $root,
        FilesystemIterator::SKIP_DOTS | FilesystemIterator::FOLLOW_SYMLINKS | FilesystemIterator::CURRENT_AS_FILEINFO
    ),
    RecursiveIteratorIterator::LEAVES_ONLY
);
$seenRealPaths = [];
foreach ($iterator as $info) {
    /** @var SplFileInfo $info */
    if (!$info->isFile() || strtolower($info->getExtension()) !== 'php') {
        continue;
    }
    $path = $info->getPathname();
    // Guard against symlink cycles: visit each real path once.
    $real = realpath($path) ?: $path;
    if (isset($seenRealPaths[$real])) {
        continue;
    }
    $seenRealPaths[$real] = true;
    if (preg_match('{(^|/)\.git(/|$)}', $path)) {
        continue;
    }
    $extracted = extract_classes((string)@file_get_contents($path));
    foreach ($extracted['classes'] as $class) {
        $declarationCounts[$class] = ($declarationCounts[$class] ?? 0) + 1;
        if (!isset($map[$class])) {
            $map[$class] = $path;
        }
    }
    foreach ($extracted['protobuf'] as $class) {
        $protobufClasses[$class] = true;
    }
}

if (!is_dir($out . '/composer')) {
    @mkdir($out . '/composer', 0777, true);
}
file_put_contents($out . '/composer/autoload_classmap.php', '<?php return ' . var_export($map, true) . ';' . "\n");
file_put_contents($out . '/autoload.php', "<?php // generated by php-portable: class map only\n");
file_put_contents($out . '/classes.txt', implode("\n", array_keys($map)) . "\n");
file_put_contents($out . '/protobuf-classes.txt', implode("\n", array_keys($protobufClasses)) . "\n");
// Classes declared in more than one file: env-variant config classes (framework loads one per
// environment) — member checks against any single variant produce false positives.
$variantClasses = array_keys(array_filter($declarationCounts, fn($c) => $c > 1));
file_put_contents($out . '/variant-classes.txt', implode("\n", $variantClasses) . "\n");
echo count($map), ' classes, ', count($protobufClasses), ' protobuf, ', count($variantClasses), " variants, ", count($seenRealPaths), " files\n";
