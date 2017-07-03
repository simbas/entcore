import http from 'axios'

export class ImportCSVService {

    private constructor() {}

    public static async  uploadCSV(importInfos): Promise<any> {
     //   let  headers = { 'Content-Type': 'multipart/form-data' }
        let formData = new FormData();
        for(let key in importInfos){
            formData.append(key, importInfos[key]);
        }
        let response;
        try {
             response = await http.post('directory/wizard/validate', 
                formData, {'headers' : { 'Content-Type': 'multipart/form-data' }});
          } catch(error) {
            return error.response.data;
          }
          return response.data;
        }
}