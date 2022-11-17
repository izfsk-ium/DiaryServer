function checkType(any: any) {
    return Object.prototype.toString.call(any).slice(8, -1)
}
export function clone(any: any): any {
    if (checkType(any) === 'Object') { // 拷贝对象
        let o: any = {};
        for (let key in any) {
            o[key] = clone(any[key])
        }
        return o;
    } else if (checkType(any) === 'Array') { // 拷贝数组
        var arr = []
        for (let i = 0, leng = any.length; i < leng; i++) {
            arr[i] = clone(any[i])
        }
        return arr;
    } else if (checkType(any) === 'Date') { // 拷贝日期
        return new Date(any.valueOf())
    } else if (checkType(any) === 'RegExp') { // 拷贝正则
        return new RegExp(any)
    } else if (checkType(any) === 'Map') { // 拷贝Map 集合
        let m = new Map()
        any.forEach((v: any, k: any) => {
            m.set(k, clone(v))
        })
        return m
    } else if (checkType(any) === 'Set') { // 拷贝Set 集合
        let s = new Set()
        for (let val of any.values()) {
            s.add(clone(val))
        }
        return s
    }
    return any;
}