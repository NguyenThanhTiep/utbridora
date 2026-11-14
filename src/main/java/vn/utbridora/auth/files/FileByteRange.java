package vn.utbridora.auth.files;

import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** A single RFC byte range; called only after current resource authorization and CLEAN checks. */
record FileByteRange(long start,long end,boolean partial) {
    long length() {return end-start+1;}
    static FileByteRange parse(String header,long size) {
        if(header==null) return new FileByteRange(0,size-1,false);
        if(!header.matches("bytes=[0-9]*-[0-9]*")) throw failure(416,"INVALID_RANGE");
        String[] bounds=header.substring(6).split("-",-1);
        try {
            if(bounds[0].isEmpty()) {
                long suffix=Long.parseLong(bounds[1]);if(suffix<=0) throw failure(416,"INVALID_RANGE");
                return new FileByteRange(Math.max(0,size-suffix),size-1,true);
            }
            long start=Long.parseLong(bounds[0]);long end=bounds[1].isEmpty()?size-1:Long.parseLong(bounds[1]);
            if(start>=size || end<start) throw failure(416,"INVALID_RANGE");
            return new FileByteRange(start,Math.min(end,size-1),true);
        } catch(NumberFormatException invalid) {throw failure(416,"INVALID_RANGE");}
    }
}
