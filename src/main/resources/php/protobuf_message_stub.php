<?php
/**
 * php-portable: stub for the runtime-provided \ProtobufMessage base class.
 *
 * Legacy protobuf-style codebases (e.g. swoole_system services) declare every message as
 * `class X extends \ProtobufMessage`, where the base class is provided by a proprietary
 * PHP runtime — it is not in the project, not a bundled extension, and not in phpstorm-stubs.
 * Without a declaration, static analysis cannot resolve the inheritance chain: every
 * inherited method (parseFromString, serializeToString, …) is reported as a false
 * `Method "X" does not exist` and WorseReflection degrades the message type.
 *
 * The stub declares the runtime API surface those codebases use. Method bodies are
 * intentionally empty — reflection only needs the signatures.
 */
class ProtobufMessage
{
    /** @var array */
    protected static $fields = array();

    public function reset()
    {
    }

    /**
     * @return string
     */
    public function serializeToString()
    {
        return '';
    }

    /**
     * @param string $data
     */
    public function parseFromString($data)
    {
    }

    /**
     * The runtime resolves generated accessors (get*, set*, append*, clear*, …At, …Iterator)
     * dynamically per message field descriptor.
     *
     * @param string $name
     * @param array $arguments
     * @return mixed
     */
    public function __call($name, $arguments)
    {
        return null;
    }
}
