local identity = ARGV[1]
local pending = identity .. ':PENDING'
local linked = identity .. ':LINKED'
local current = redis.call('GET', KEYS[1])
if current == linked then
    return 2
end
if current ~= pending or redis.call('GET', KEYS[2]) ~= identity then
    return 0
end
redis.call('SET', KEYS[1], linked, 'EX', ARGV[2])
redis.call('DEL', KEYS[2])
if redis.call('GET', KEYS[3]) == ARGV[3] then
    redis.call('DEL', KEYS[3])
end
return 1
