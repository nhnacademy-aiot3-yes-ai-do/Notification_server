local identity = ARGV[1]
if redis.call('GET', KEYS[1]) ~= identity .. ':PENDING'
        or redis.call('GET', KEYS[2]) ~= identity
        or redis.call('GET', KEYS[3]) ~= ARGV[2] then
    return 0
end
redis.call('EXPIRE', KEYS[1], ARGV[3])
redis.call('EXPIRE', KEYS[2], ARGV[3])
return 1
