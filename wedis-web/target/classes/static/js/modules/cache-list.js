var v = new Vue({
	el:"#app",
	data:function(){
		return {
			cacheData:[
			],
			test: null
		};
	},
	methods:{
		
	}
});

axios.get('/cache/list/'+c.queryString('id')+'/0/')
	.then(function(resp){
		if(resp.data.code==200){
			let data = resp.data.content;
			console.log(data);
			cacheData = [-84, -19, 0, 5, 116, 0, 2, 98, 98];
		}
	});