if redis.call('GET', KEYS[3]) ~= ARGV[3] then
    return 0
end
redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
redis.call('DEL', KEYS[2])
redis.call('DEL', KEYS[3])
return 1
