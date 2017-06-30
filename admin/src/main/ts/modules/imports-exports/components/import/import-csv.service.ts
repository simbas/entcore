import http from 'axios'

export class ImportCSVService {

    private constructor() {}

    public static uploadCSV(importInfos): Promise<any> {
     //   let  headers = { 'Content-Type': 'multipart/form-data' }
        let formData = new FormData();
        for(let key in importInfos){
            formData.append(key, importInfos[key]);
        }

        return http.post('directory/wizard/validate', formData, {'headers' : { 'Content-Type': 'multipart/form-data' }})
            .then(res => {
                console.log(res.data)
            }).catch(err => {
                console.log(err)
            })
        }
}